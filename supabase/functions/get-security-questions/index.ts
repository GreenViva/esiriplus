// functions/get-security-questions/index.ts
// Returns the list of recovery question definitions.
// Public endpoint (no auth required) — patients need these before setup.
// Rate limited: 10/min per IP

import { handlePreflight } from "../_shared/cors.ts";
import { checkRateLimit } from "../_shared/rateLimit.ts";
import { errorResponse, successResponse } from "../_shared/errors.ts";
import { getServiceClient } from "../_shared/supabase.ts";
import { getClientIp } from "../_shared/logger.ts";

Deno.serve(async (req: Request) => {
  const origin   = req.headers.get("origin");
  const clientIp = getClientIp(req) ?? "unknown";

  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    if (req.method !== "POST" && req.method !== "GET") {
      return new Response("Method not allowed", { status: 405 });
    }

    await checkRateLimit(`get-questions:${clientIp}`, 10, 60);

    const supabase = getServiceClient();

    const { data, error } = await supabase
      .from("recovery_question_definitions")
      .select("question_key, question_text")
      .order("question_key");

    if (error) throw error;

    const questions = (data ?? []).map((row: { question_key: string; question_text: string }) => ({
      key: row.question_key,
      label: row.question_text,
    }));

    return successResponse(questions, 200, origin);
  } catch (err) {
    return errorResponse(err, origin);
  }
});
