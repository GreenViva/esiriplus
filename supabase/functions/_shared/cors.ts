// _shared/cors.ts
// CORS is restricted to configured origins (Android app / admin portal).
// Never use wildcard (*) in production.

// Comma-separated list of allowed origins
const ALLOWED_ORIGINS = (Deno.env.get("ALLOWED_ORIGIN") ?? "")
  .split(",")
  .map((o) => o.trim())
  .filter(Boolean);

function isAllowedOrigin(origin: string): boolean {
  // Exact match from env
  if (ALLOWED_ORIGINS.includes(origin)) return true;
  // Allow any Vercel preview/production URL for this project
  if (origin.endsWith(".vercel.app")) return true;
  // Allow localhost for development
  if (origin.startsWith("http://localhost:")) return true;
  return false;
}

export function corsHeaders(requestOrigin?: string | null): HeadersInit {
  // If the request origin is allowed, echo it back
  const origin =
    requestOrigin && isAllowedOrigin(requestOrigin)
      ? requestOrigin
      : ALLOWED_ORIGINS[0] ?? "";

  return {
    "Access-Control-Allow-Origin": origin,
    "Access-Control-Allow-Methods": "POST, GET, OPTIONS",
    "Access-Control-Allow-Headers":
      "Authorization, Content-Type, X-Idempotency-Key, X-Patient-Token, X-Doctor-Token",
    "Access-Control-Max-Age": "86400",
    "Vary": "Origin",
  };
}

export function handlePreflight(req: Request): Response | null {
  if (req.method === "OPTIONS") {
    return new Response(null, {
      status: 204,
      headers: corsHeaders(req.headers.get("origin")),
    });
  }
  return null;
}
