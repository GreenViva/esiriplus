import { createServerClient } from "@supabase/ssr";
import { NextResponse, type NextRequest } from "next/server";

const ALLOWED_ROLES = ["admin", "hr", "finance", "audit"];

// Simple in-memory rate limiter for auth endpoints
const rateLimitMap = new Map<string, { count: number; resetAt: number }>();
const RATE_LIMIT_WINDOW = 60_000; // 1 minute
const RATE_LIMIT_MAX = 10; // max requests per window

function isRateLimited(ip: string): boolean {
  const now = Date.now();
  const entry = rateLimitMap.get(ip);

  if (!entry || now > entry.resetAt) {
    rateLimitMap.set(ip, { count: 1, resetAt: now + RATE_LIMIT_WINDOW });
    return false;
  }

  entry.count++;
  if (entry.count > RATE_LIMIT_MAX) return true;
  return false;
}

// Periodic cleanup to prevent memory leak (runs every 100 checks)
let cleanupCounter = 0;
function maybeCleanupRateLimit() {
  cleanupCounter++;
  if (cleanupCounter < 100) return;
  cleanupCounter = 0;
  const now = Date.now();
  rateLimitMap.forEach((entry, key) => {
    if (now > entry.resetAt) rateLimitMap.delete(key);
  });
}

/**
 * Derive a signing key from SUPABASE_SERVICE_ROLE_KEY (available in Edge/Node middleware).
 * We use a simple HMAC-like approach: SHA-256 hash of (secret + payload).
 * This prevents cookie tampering without needing a separate COOKIE_SECRET env var.
 */
async function signPayload(payload: string): Promise<string> {
  const secret = process.env.SUPABASE_SERVICE_ROLE_KEY ?? "";
  const encoder = new TextEncoder();
  const key = await crypto.subtle.importKey(
    "raw",
    encoder.encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"],
  );
  const sig = await crypto.subtle.sign("HMAC", key, encoder.encode(payload));
  return Array.from(new Uint8Array(sig))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

async function verifySignedCookie(
  raw: string | undefined,
): Promise<string[] | null> {
  if (!raw) return null;
  const idx = raw.lastIndexOf(".");
  if (idx < 0) return null;

  const payload = raw.slice(0, idx);
  const signature = raw.slice(idx + 1);
  const expected = await signPayload(payload);

  if (signature !== expected) return null;

  try {
    const parsed = JSON.parse(payload);
    if (Array.isArray(parsed) && parsed.every((r) => typeof r === "string")) {
      return parsed as string[];
    }
  } catch {
    // malformed
  }
  return null;
}

/** Create signed cookie value: JSON_PAYLOAD.HMAC_SIGNATURE */
async function createSignedCookie(roles: string[]): Promise<string> {
  const payload = JSON.stringify(roles);
  const sig = await signPayload(payload);
  return `${payload}.${sig}`;
}

/** Append security headers to a response */
function applySecurityHeaders(response: NextResponse): void {
  // Clickjacking protection
  response.headers.set("X-Frame-Options", "DENY");
  // Prevent MIME-type sniffing
  response.headers.set("X-Content-Type-Options", "nosniff");
  // Referrer policy
  response.headers.set("Referrer-Policy", "strict-origin-when-cross-origin");
  // Permissions policy — disable unused browser features
  response.headers.set(
    "Permissions-Policy",
    "camera=(), microphone=(), geolocation=(), payment=()",
  );
  // Content Security Policy
  response.headers.set(
    "Content-Security-Policy",
    [
      "default-src 'self'",
      "script-src 'self' 'unsafe-inline' 'unsafe-eval'",
      "style-src 'self' 'unsafe-inline'",
      "img-src 'self' *.supabase.co data: blob:",
      "font-src 'self' data:",
      "connect-src 'self' *.supabase.co wss://*.supabase.co",
      "frame-ancestors 'none'",
      "base-uri 'self'",
      "form-action 'self'",
    ].join("; "),
  );
  // HSTS — enforce HTTPS
  response.headers.set(
    "Strict-Transport-Security",
    "max-age=63072000; includeSubDomains; preload",
  );
}

export async function middleware(request: NextRequest) {
  let supabaseResponse = NextResponse.next({ request });

  const supabase = createServerClient(
    process.env.NEXT_PUBLIC_SUPABASE_URL!,
    process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!,
    {
      cookies: {
        getAll() {
          return request.cookies.getAll();
        },
        setAll(
          cookiesToSet: {
            name: string;
            value: string;
            options?: Record<string, unknown>;
          }[],
        ) {
          cookiesToSet.forEach(({ name, value }) =>
            request.cookies.set(name, value),
          );
          supabaseResponse = NextResponse.next({ request });
          cookiesToSet.forEach(({ name, value, options }) =>
            supabaseResponse.cookies.set(name, value, options),
          );
        },
      },
    },
  );

  const pathname = request.nextUrl.pathname;

  // Apply security headers to ALL matched routes
  applySecurityHeaders(supabaseResponse);

  // ── Auth API routes with rate limiting ─────────────────────────────────
  if (pathname === "/api/auth/set-roles" || pathname === "/api/auth/clear-roles") {
    maybeCleanupRateLimit();
    const clientIp = request.headers.get("x-forwarded-for")?.split(",")[0]?.trim()
      ?? request.headers.get("x-real-ip")
      ?? "unknown";

    if (isRateLimited(clientIp)) {
      const response = NextResponse.json(
        { error: "Too many requests" },
        { status: 429 },
      );
      response.headers.set("Retry-After", "60");
      applySecurityHeaders(response);
      return response;
    }

    if (pathname === "/api/auth/set-roles") return handleSetRoles(request);
    return handleClearRoles();
  }

  // ── Dashboard protection ────────────────────────────────────────────────
  if (!pathname.startsWith("/dashboard")) {
    return supabaseResponse;
  }

  const {
    data: { session },
  } = await supabase.auth.getSession();

  if (!session) {
    const url = request.nextUrl.clone();
    url.pathname = "/login";
    const redirect = NextResponse.redirect(url);
    applySecurityHeaders(redirect);
    return redirect;
  }

  // Read and verify signed roles cookie
  const rolesCookieRaw = request.cookies.get("portal_roles")?.value;
  const userRoles = await verifySignedCookie(rolesCookieRaw);

  if (!userRoles || !userRoles.some((r) => ALLOWED_ROLES.includes(r))) {
    const url = request.nextUrl.clone();
    url.pathname = "/login";
    url.searchParams.set("error", "access_denied");
    const redirect = NextResponse.redirect(url);
    // Clear invalid cookie
    redirect.cookies.set("portal_roles", "", {
      path: "/",
      maxAge: 0,
      httpOnly: true,
      secure: process.env.NODE_ENV === "production",
      sameSite: "lax",
    });
    applySecurityHeaders(redirect);
    return redirect;
  }

  // Route-based role enforcement
  const routeRules: { prefix: string; roles: string[] }[] = [
    { prefix: "/dashboard/users", roles: ["admin"] },
    { prefix: "/dashboard/doctors", roles: ["admin", "hr"] },
    { prefix: "/dashboard/ratings", roles: ["admin", "hr"] },
    { prefix: "/dashboard/payments", roles: ["admin", "finance"] },
    { prefix: "/dashboard/analytics", roles: ["admin", "finance"] },
    { prefix: "/dashboard/audit", roles: ["admin", "audit"] },
    { prefix: "/dashboard/hr/audit", roles: ["admin", "hr", "audit"] },
    { prefix: "/dashboard/hr", roles: ["admin", "hr"] },
  ];

  for (const rule of routeRules) {
    if (pathname.startsWith(rule.prefix)) {
      const canAccess = userRoles.some((r) => rule.roles.includes(r));
      if (!canAccess) {
        const url = request.nextUrl.clone();
        if (userRoles.includes("hr")) {
          url.pathname = "/dashboard/hr";
        } else if (userRoles.includes("finance")) {
          url.pathname = "/dashboard/payments";
        } else if (userRoles.includes("audit")) {
          url.pathname = "/dashboard/audit";
        } else {
          url.pathname = "/dashboard";
        }
        const redirect = NextResponse.redirect(url);
        applySecurityHeaders(redirect);
        return redirect;
      }
      break;
    }
  }

  return supabaseResponse;
}

// ── API route: set signed, httpOnly role cookie ─────────────────────────────
// Called by login page after successful auth + role verification.
async function handleSetRoles(request: NextRequest): Promise<NextResponse> {
  if (request.method !== "POST") {
    return NextResponse.json({ error: "Method not allowed" }, { status: 405 });
  }

  try {
    const body = await request.json();
    const roles = body.roles;

    if (
      !Array.isArray(roles) ||
      roles.length === 0 ||
      !roles.every((r: unknown) => typeof r === "string" && ALLOWED_ROLES.includes(r as string))
    ) {
      return NextResponse.json({ error: "Invalid roles" }, { status: 400 });
    }

    // Verify the caller has a valid Supabase session
    const supabase = createServerClient(
      process.env.NEXT_PUBLIC_SUPABASE_URL!,
      process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY!,
      {
        cookies: {
          getAll() {
            return request.cookies.getAll();
          },
          setAll() {
            // no-op for this request
          },
        },
      },
    );

    const {
      data: { session },
    } = await supabase.auth.getSession();

    if (!session) {
      return NextResponse.json({ error: "Not authenticated" }, { status: 401 });
    }

    const signedValue = await createSignedCookie(roles as string[]);
    const response = NextResponse.json({ ok: true });

    response.cookies.set("portal_roles", signedValue, {
      path: "/",
      maxAge: 3600, // 1 hour (reduced from 24h)
      httpOnly: true,
      secure: process.env.NODE_ENV === "production",
      sameSite: "lax",
    });

    applySecurityHeaders(response);
    return response;
  } catch {
    return NextResponse.json({ error: "Bad request" }, { status: 400 });
  }
}

// ── API route: clear role cookie on logout ──────────────────────────────────
function handleClearRoles(): NextResponse {
  const response = NextResponse.json({ ok: true });
  response.cookies.set("portal_roles", "", {
    path: "/",
    maxAge: 0,
    httpOnly: true,
    secure: process.env.NODE_ENV === "production",
    sameSite: "lax",
  });
  applySecurityHeaders(response);
  return response;
}

export const config = {
  matcher: ["/dashboard/:path*", "/api/auth/:path*"],
};
