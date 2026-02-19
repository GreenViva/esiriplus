// _shared/cors.ts
// CORS is restricted to the configured origin (Android app / portal).
// Never use wildcard (*) in production.

const ALLOWED_ORIGIN = Deno.env.get("ALLOWED_ORIGIN") ?? "";

export function corsHeaders(requestOrigin?: string | null): HeadersInit {
  const origin =
    requestOrigin && requestOrigin === ALLOWED_ORIGIN
      ? ALLOWED_ORIGIN
      : ALLOWED_ORIGIN; // still enforce configured origin

  return {
    "Access-Control-Allow-Origin": origin,
    "Access-Control-Allow-Methods": "POST, GET, OPTIONS",
    "Access-Control-Allow-Headers":
      "Authorization, Content-Type, X-Idempotency-Key",
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
