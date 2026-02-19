// _shared/errors.ts
// Standardised error responses — sensitive internals are never leaked.

import { corsHeaders } from "./cors.ts";
import { AuthError } from "./auth.ts";
import { RateLimitError } from "./rateLimit.ts";

export interface ApiError {
  error: string;
  code: string;
}

export function errorResponse(
  error: unknown,
  requestOrigin?: string | null
): Response {
  const headers = {
    ...corsHeaders(requestOrigin),
    "Content-Type": "application/json",
  };

  if (error instanceof AuthError) {
    return new Response(
      JSON.stringify({ error: error.message, code: "UNAUTHORIZED" }),
      { status: error.status, headers }
    );
  }

  if (error instanceof RateLimitError) {
    return new Response(
      JSON.stringify({ error: error.message, code: "RATE_LIMITED" }),
      {
        status: 429,
        headers: {
          ...headers,
          "Retry-After": error.retryAfter.toString(),
        },
      }
    );
  }

  if (error instanceof ValidationError) {
    return new Response(
      JSON.stringify({ error: error.message, code: "VALIDATION_ERROR", fields: error.fields }),
      { status: 400, headers }
    );
  }

  // Generic internal error — never expose details
  console.error("Unhandled error:", error);
  return new Response(
    JSON.stringify({ error: "Internal server error", code: "INTERNAL_ERROR" }),
    { status: 500, headers }
  );
}

export class ValidationError extends Error {
  constructor(message: string, public fields?: Record<string, string>) {
    super(message);
  }
}

export function successResponse(
  data: unknown,
  status = 200,
  requestOrigin?: string | null
): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      ...corsHeaders(requestOrigin),
      "Content-Type": "application/json",
    },
  });
}

/** Quick method-not-allowed guard */
export function requireMethod(req: Request, ...methods: string[]): void {
  if (!methods.includes(req.method)) {
    throw Object.assign(new Error(`Method ${req.method} not allowed`), {
      status: 405,
    });
  }
}
