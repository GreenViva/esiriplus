// functions/get-vapid-key/index.ts
// Returns the VAPID public key for web push subscription.
// Public endpoint (no auth needed — only returns public key).
// Rate limit: 30/min.

import { handlePreflight } from "../_shared/cors.ts";
import { checkRateLimit } from "../_shared/rateLimit.ts";
import { errorResponse, successResponse } from "../_shared/errors.ts";
import { getClientIp } from "../_shared/logger.ts";

const VAPID_PUBLIC_KEY = Deno.env.get("VAPID_PUBLIC_KEY")!;

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    if (req.method !== "GET") {
      return new Response("Method not allowed", { status: 405 });
    }

    const clientIp = getClientIp(req) ?? "unknown";
    await checkRateLimit(`vapid:${clientIp}`, 30, 60);

    if (!VAPID_PUBLIC_KEY) {
      throw new Error("VAPID public key not configured");
    }

    return successResponse({
      vapid_public_key: VAPID_PUBLIC_KEY,
    }, 200, origin);

  } catch (err) {
    return errorResponse(err, origin);
  }
});
