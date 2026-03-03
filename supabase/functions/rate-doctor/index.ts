import { handlePreflight } from "../_shared/cors.ts";
import { validateAuth } from "../_shared/auth.ts";
import {
  errorResponse,
  successResponse,
  ValidationError,
} from "../_shared/errors.ts";
import { getServiceClient } from "../_shared/supabase.ts";
import { LIMITS } from "../_shared/rateLimit.ts";

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    const auth = await validateAuth(req);

    // Only patients can rate
    if (auth.role !== "patient" || !auth.sessionId) {
      throw new ValidationError("Only patients can submit ratings");
    }

    await LIMITS.sensitive(auth.sessionId);

    const body = await req.json();
    const { consultation_id, rating, comment } = body;

    // ── Input validation ──────────────────────────────────────────────────
    if (!consultation_id || typeof consultation_id !== "string") {
      throw new ValidationError("consultation_id is required");
    }

    const stars = Math.floor(Number(rating));
    if (isNaN(stars) || stars < 1 || stars > 5) {
      throw new ValidationError("rating must be an integer between 1 and 5");
    }

    const trimmedComment =
      typeof comment === "string" ? comment.trim() : null;

    if (stars <= 3 && (!trimmedComment || trimmedComment.length === 0)) {
      throw new ValidationError(
        "Comment is required for ratings of 3 or below"
      );
    }

    const supabase = getServiceClient();

    // ── 1. Verify consultation exists and is COMPLETED ────────────────────
    const { data: consultation, error: consultErr } = await supabase
      .from("consultations")
      .select(
        "consultation_id, patient_session_id, doctor_id, status"
      )
      .eq("consultation_id", consultation_id)
      .single();

    if (consultErr || !consultation) {
      console.error("[rate-doctor] Consultation lookup failed:", consultErr?.message ?? "not found", "consultation_id:", consultation_id);
      throw new ValidationError("Consultation not found");
    }

    console.log(`[rate-doctor] Consultation found: status=${consultation.status}, patient_session_id=${consultation.patient_session_id}, doctor_id=${consultation.doctor_id}`);

    if (consultation.status.toUpperCase() !== "COMPLETED") {
      throw new ValidationError(
        `Consultation is not completed — current status: ${consultation.status}`
      );
    }

    // ── 2. Verify caller is the patient of this consultation ──────────────
    if (String(consultation.patient_session_id) !== String(auth.sessionId)) {
      console.error(`[rate-doctor] Session mismatch: consultation.patient_session_id=${consultation.patient_session_id}, auth.sessionId=${auth.sessionId}`);
      throw new ValidationError(
        "You are not the patient of this consultation"
      );
    }

    // ── 3. Check no existing rating for this consultation ─────────────────
    const { data: existing, error: existingErr } = await supabase
      .from("doctor_ratings")
      .select("rating_id")
      .eq("consultation_id", consultation_id)
      .maybeSingle();

    if (existingErr) {
      console.error("[rate-doctor] Existing rating check failed:", JSON.stringify(existingErr));
    }

    if (existing) {
      throw new ValidationError(
        "This consultation has already been rated"
      );
    }

    // ── 4. Insert rating ──────────────────────────────────────────────────
    const { data: inserted, error: insertErr } = await supabase
      .from("doctor_ratings")
      .insert({
        doctor_id: consultation.doctor_id,
        consultation_id: consultation_id,
        patient_session_id: auth.sessionId,
        rating: stars,
        comment: trimmedComment || null,
      })
      .select("rating_id")
      .single();

    if (insertErr) {
      console.error("[rate-doctor] Insert rating error:", JSON.stringify(insertErr));
      throw new ValidationError(`Rating save failed: ${insertErr.message}`);
    }

    // The Postgres trigger automatically updates doctor_profiles aggregates.

    return successResponse(
      { rating_id: inserted.rating_id, ok: true },
      201,
      origin
    );
  } catch (err) {
    return errorResponse(err, origin);
  }
});
