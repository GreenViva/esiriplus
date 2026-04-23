// _shared/rateLimit.ts
// Sliding-window rate limiting backed by Upstash Redis.
// No Redis dep needed — uses the Upstash REST API directly.

const UPSTASH_URL = Deno.env.get("UPSTASH_REDIS_REST_URL")!;
const UPSTASH_TOKEN = Deno.env.get("UPSTASH_REDIS_REST_TOKEN")!;

export class RateLimitError extends Error {
  constructor(public retryAfter: number) {
    super(`Rate limit exceeded. Retry after ${retryAfter}s`);
  }
}

/**
 * Sliding window rate limit check via Upstash Redis.
 *
 * @param identifier  Unique key, e.g. "payment:user_abc123"
 * @param limit       Max requests allowed in the window
 * @param windowSecs  Window duration in seconds
 */
export async function checkRateLimit(
  identifier: string,
  limit: number,
  windowSecs: number
): Promise<void> {
  if (!UPSTASH_URL || !UPSTASH_TOKEN) {
    // If Redis is not configured, skip rate limiting (dev mode only)
    console.warn("Rate limiting skipped — Upstash not configured");
    return;
  }

  const now = Date.now();
  const windowStart = now - windowSecs * 1000;
  const key = `ratelimit:${identifier}`;

  // Pipeline: ZREMRANGEBYSCORE + ZADD + ZCARD + EXPIRE
  const pipeline = [
    ["ZREMRANGEBYSCORE", key, "-inf", windowStart.toString()],
    ["ZADD", key, now.toString(), `${now}-${Math.random()}`],
    ["ZCARD", key],
    ["EXPIRE", key, windowSecs.toString()],
  ];

  const res = await fetch(`${UPSTASH_URL}/pipeline`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${UPSTASH_TOKEN}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(pipeline),
  });

  if (!res.ok) {
    // On Redis failure, fail open (don't block the request)
    console.error("Rate limit Redis error:", await res.text());
    return;
  }

  const results: { result: number }[] = await res.json();
  const currentCount = results[2]?.result ?? 0;

  if (currentCount > limit) {
    throw new RateLimitError(windowSecs);
  }
}

// Pre-configured limiters for common patterns
export const LIMITS = {
  /** Payment functions: 10 req/min */
  payment: (userId: string) =>
    checkRateLimit(`payment:${userId}`, 10, 60),

  /** Read/query functions: 30 req/min */
  read: (userId: string) =>
    checkRateLimit(`read:${userId}`, 30, 60),

  /** Notification functions: 20 req/min */
  notification: (userId: string) =>
    checkRateLimit(`notification:${userId}`, 20, 60),

  /** Auth sensitive: 5 req/min */
  sensitive: (userId: string) =>
    checkRateLimit(`sensitive:${userId}`, 5, 60),

  /** Message sending: 60 req/min */
  message: (userId: string) =>
    checkRateLimit(`message:${userId}`, 60, 60),

  /** Video/call token: 600 req/min (effectively unlimited; abuse-only guard).
   *  Real ceiling is the VideoSDK plan, not this limiter. */
  video: (userId: string) =>
    checkRateLimit(`video:${userId}`, 600, 60),

  /** Consultation requests: 15 req/min */
  consultation: (userId: string) =>
    checkRateLimit(`consultation:${userId}`, 15, 60),
};

/**
 * Global concurrency guard for expensive operations.
 * Uses a Redis counter with short TTL to limit how many
 * simultaneous executions of a given operation can run.
 *
 * Returns a release function that MUST be called when done.
 */
export async function acquireConcurrencySlot(
  operation: string,
  maxConcurrent: number,
  ttlSecs: number = 30,
): Promise<() => Promise<void>> {
  if (!UPSTASH_URL || !UPSTASH_TOKEN) {
    return async () => {}; // no-op in dev
  }

  const key = `concurrency:${operation}`;

  const incrRes = await fetch(`${UPSTASH_URL}/pipeline`, {
    method: "POST",
    headers: {
      Authorization: `Bearer ${UPSTASH_TOKEN}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify([
      ["INCR", key],
      ["EXPIRE", key, ttlSecs.toString()],
    ]),
  });

  if (!incrRes.ok) {
    // Fail open
    return async () => {};
  }

  const results: { result: number }[] = await incrRes.json();
  const current = results[0]?.result ?? 0;

  const release = async () => {
    try {
      await fetch(`${UPSTASH_URL}/DECR/${key}`, {
        headers: { Authorization: `Bearer ${UPSTASH_TOKEN}` },
      });
    } catch { /* best-effort */ }
  };

  if (current > maxConcurrent) {
    await release();
    throw new RateLimitError(5);
  }

  return release;
}
