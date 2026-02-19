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
};
