// functions/log-doctor-online/index.ts
// Logs doctor online/offline timestamps for compliance monitoring.
// Called by DoctorOnlineService on start (online) and cleanup (offline).
// Rate limit: 10/min.

import { handlePreflight } from "../_shared/cors.ts";
import { validateAuth, requireRole } from "../_shared/auth.ts";
import { LIMITS } from "../_shared/rateLimit.ts";
import { errorResponse, successResponse, ValidationError } from "../_shared/errors.ts";
import { getServiceClient } from "../_shared/supabase.ts";

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    const auth = await validateAuth(req);
    requireRole(auth, "doctor");
    await LIMITS.read(auth.userId ?? "anon");

    const body = await req.json();
    const action = body?.action;

    if (action !== "online" && action !== "offline") {
      throw new ValidationError("action must be 'online' or 'offline'");
    }

    const supabase = getServiceClient();
    const doctorId = auth.userId!;

    if (action === "online") {
      // Close any stale open sessions (> 12 hours) as safety net
      await supabase
        .from("doctor_online_log")
        .update({
          went_offline_at: new Date().toISOString(),
          duration_minutes: 720, // cap at 12h
        })
        .eq("doctor_id", doctorId)
        .is("went_offline_at", null)
        .lt("went_online_at", new Date(Date.now() - 12 * 60 * 60 * 1000).toISOString());

      // Insert new online session
      const { data, error } = await supabase
        .from("doctor_online_log")
        .insert({
          doctor_id: doctorId,
          went_online_at: new Date().toISOString(),
        })
        .select("log_id")
        .single();

      if (error) {
        console.error("[log-doctor-online] Insert failed:", error.message);
        throw new Error("Failed to log online status");
      }

      console.log(`[log-doctor-online] Doctor ${doctorId} went online, log_id=${data.log_id}`);
      return successResponse({ log_id: data.log_id }, 200, origin);
    }

    // action === "offline"
    // Find the most recent open session for this doctor
    const { data: openLog, error: fetchErr } = await supabase
      .from("doctor_online_log")
      .select("log_id, went_online_at")
      .eq("doctor_id", doctorId)
      .is("went_offline_at", null)
      .order("went_online_at", { ascending: false })
      .limit(1)
      .single();

    if (fetchErr || !openLog) {
      console.warn(`[log-doctor-online] No open session found for doctor ${doctorId}`);
      return successResponse({ closed: false, message: "No open session" }, 200, origin);
    }

    const onlineAt = new Date(openLog.went_online_at);
    const now = new Date();
    const durationMinutes = Math.round((now.getTime() - onlineAt.getTime()) / 60000);

    const { error: updateErr } = await supabase
      .from("doctor_online_log")
      .update({
        went_offline_at: now.toISOString(),
        duration_minutes: Math.min(durationMinutes, 720), // cap at 12h
      })
      .eq("log_id", openLog.log_id);

    if (updateErr) {
      console.error("[log-doctor-online] Update failed:", updateErr.message);
      throw new Error("Failed to log offline status");
    }

    console.log(`[log-doctor-online] Doctor ${doctorId} went offline after ${durationMinutes} minutes`);
    return successResponse({ closed: true, duration_minutes: durationMinutes }, 200, origin);

  } catch (err) {
    return errorResponse(err, origin);
  }
});
