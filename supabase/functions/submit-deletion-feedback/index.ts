// submit-deletion-feedback: Optional feedback the patient leaves on the
// way out. Stored anonymously in patient_deletion_feedback (no FK to the
// session — the row outlives the 30-day purge of the patient's data).
//
// Body shape:
//   { reasons: string[], comment?: string, locale?: string }
//
// `reasons` is an array of short canonical codes the client picks from
// (e.g. "no_longer_needed", "privacy", "too_slow", "bad_experience",
// "other"). The admin panel renders the localized labels; the database
// stores the codes so a future locale change doesn't break aggregation.

import { corsHeaders, handleCors } from "../_shared/cors.ts";
import { handleError } from "../_shared/errors.ts";
import { validateAuth, requireRole } from "../_shared/auth.ts";
import { getServiceClient } from "../_shared/supabase.ts";

const REASON_CODES = new Set([
  "no_longer_needed",
  "privacy",
  "too_slow",
  "bad_experience",
  "other",
]);

const MAX_COMMENT_LENGTH = 2_000;

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return handleCors(req);

  try {
    const auth = await validateAuth(req);
    requireRole(auth, "patient");

    const body = await req.json().catch(() => ({}));
    const rawReasons = Array.isArray(body?.reasons) ? body.reasons : [];
    const reasons = rawReasons
      .filter((r: unknown): r is string => typeof r === "string")
      .filter((r: string) => REASON_CODES.has(r));

    const rawComment = typeof body?.comment === "string" ? body.comment.trim() : null;
    const comment = rawComment && rawComment.length > 0
      ? rawComment.slice(0, MAX_COMMENT_LENGTH)
      : null;

    const locale = typeof body?.locale === "string" && body.locale.length <= 16
      ? body.locale
      : null;

    if (reasons.length === 0 && !comment) {
      // Treat as a no-op — user opened the sheet then closed it. 200 so the
      // client doesn't show an error.
      return new Response(
        JSON.stringify({ recorded: false }),
        { status: 200, headers: { ...corsHeaders(req), "Content-Type": "application/json" } },
      );
    }

    const supabase = getServiceClient();
    const { error } = await supabase
      .from("patient_deletion_feedback")
      .insert({ reasons, comment, locale });

    if (error) {
      console.error("[submit-deletion-feedback] insert error:", error);
      return new Response(
        JSON.stringify({ error: error.message }),
        { status: 500, headers: { ...corsHeaders(req), "Content-Type": "application/json" } },
      );
    }

    return new Response(
      JSON.stringify({ recorded: true }),
      { status: 200, headers: { ...corsHeaders(req), "Content-Type": "application/json" } },
    );
  } catch (err) {
    return handleError(err, req);
  }
});
