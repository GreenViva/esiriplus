// functions/royal-checkin-escalation-callback/index.ts
//
// Endpoint for the Royal check-in CO escalation flow. Mirrors the shape of
// medication-reminder-callback but for the Royal slot escalation:
//
//   accept_ring               — CO accepts the 60s ring (status → co_accepted).
//   decline_ring              — CO declines; reset to pending so cron rerings
//                                a different CO (and bumps reassign_count).
//   list_active_escalations   — CO fetches the escalations they own that are
//                                still open + the doctor's Royal patient list
//                                they're meant to call.
//   start_call                — CO taps Call on a patient row. Creates a
//                                VideoSDK room, inserts a call tracking row,
//                                pushes the incoming call to the patient.
//   end_call                  — CO has hung up. Records duration + whether
//                                the patient picked up. qualifies_for_payment
//                                is computed by the DB column.
//   complete_escalation       — CO marks the escalation done. Writes
//                                doctor_earnings rows for each qualifying
//                                call (2,000 TZS each), marks the reminder
//                                acknowledged, and sets the doctor's
//                                warning_message. The existing trigger on
//                                doctor_profiles auto-increments
//                                warning_count and resets the ack flag.

import { handlePreflight } from "../_shared/cors.ts";
import { validateAuth } from "../_shared/auth.ts";
import { errorResponse, successResponse, ValidationError } from "../_shared/errors.ts";
import { logEvent } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

const CO_PER_CALL_EARNING = 2000;        // TZS per qualifying check-in call
const VIDEOSDK_API_KEY = Deno.env.get("VIDEOSDK_API_KEY")!;
const VIDEOSDK_SECRET  = Deno.env.get("VIDEOSDK_SECRET")!;
const TOKEN_EXPIRY_SECS = 7200;

// ── VideoSDK helpers (mirrors medication-reminder-cron) ──────────────────────

async function createVideoSDKToken(permissions: string[]): Promise<string> {
  const header = { alg: "HS256", typ: "JWT" };
  const now = Math.floor(Date.now() / 1000);
  const payload: Record<string, unknown> = {
    apikey: VIDEOSDK_API_KEY, permissions,
    iat: now, exp: now + TOKEN_EXPIRY_SECS, version: 2,
  };
  const encode = (obj: unknown) =>
    btoa(JSON.stringify(obj)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=/g, "");
  const headerB64 = encode(header);
  const payloadB64 = encode(payload);
  const sigInput = `${headerB64}.${payloadB64}`;
  const key = await crypto.subtle.importKey(
    "raw", new TextEncoder().encode(VIDEOSDK_SECRET),
    { name: "HMAC", hash: "SHA-256" }, false, ["sign"],
  );
  const signature = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(sigInput));
  const sigB64 = btoa(String.fromCharCode(...new Uint8Array(signature)))
    .replace(/\+/g, "-").replace(/\//g, "_").replace(/=/g, "");
  return `${sigInput}.${sigB64}`;
}

async function createRoom(): Promise<string> {
  const adminToken = await createVideoSDKToken(["allow_join"]);
  const res = await fetch("https://api.videosdk.live/v2/rooms", {
    method: "POST",
    headers: { Authorization: adminToken, "Content-Type": "application/json" },
  });
  if (!res.ok) throw new Error(`VideoSDK room creation failed: ${res.status}`);
  const data = await res.json();
  if (!data.roomId) throw new Error("No roomId returned");
  return data.roomId as string;
}

// ── Push helper — direct fetch to send-push-notification ─────────────────────

async function pushIncomingCallToPatient(args: {
  patientSessionId: string;
  consultationId: string | null;
  roomId: string;
  callId: string;
  doctorName: string;
}): Promise<void> {
  const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
  const SERVICE_ROLE = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
  const BEARER_JWT = Deno.env.get("EDGE_FN_BEARER_JWT") ?? SERVICE_ROLE;
  await fetch(`${SUPABASE_URL}/functions/v1/send-push-notification`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "Authorization": `Bearer ${BEARER_JWT}`,
      "X-Service-Key": SERVICE_ROLE,
    },
    body: JSON.stringify({
      session_id: args.patientSessionId,
      title: "Royal check-in call",
      body: `A clinical officer is calling on behalf of Dr ${args.doctorName} for your Royal check-in.`,
      type: "VIDEO_CALL_INCOMING",
      data: {
        call_id: args.callId,
        consultation_id: args.consultationId ?? "",
        room_id: args.roomId,
        call_type: "AUDIO",
        caller_role: "royal_checkin_co",
        doctor_name: args.doctorName,
      },
    }),
  });
}

// ── Auth helper ──────────────────────────────────────────────────────────────

async function requireDoctorOrCO(req: Request): Promise<{ userId: string }> {
  const auth = await validateAuth(req);
  if (!auth.userId) throw new ValidationError("Authentication required");
  return { userId: auth.userId };
}

// ── Main handler ─────────────────────────────────────────────────────────────

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    const body = await req.json().catch(() => ({} as Record<string, unknown>));
    const action = (body.action ?? "") as string;
    const supabase = getServiceClient();

    // ── list_active_escalations ──────────────────────────────────────────
    // Returns this CO's escalations that are still actionable (ringing /
    // accepted / in_progress). Each escalation is now per-patient: the row
    // carries patient_session_id + consultation_id directly.
    if (action === "list_active_escalations") {
      const { userId: coId } = await requireDoctorOrCO(req);

      const { data: escs } = await supabase
        .from("royal_checkin_escalations")
        .select(
          "escalation_id, doctor_id, slot_date, slot_hour, status, ring_expires_at, co_accepted_at, patient_session_id, consultation_id",
        )
        .eq("co_id", coId)
        .in("status", ["co_ringing","co_accepted","in_progress"])
        .order("co_notified_at", { ascending: true });

      const out: Array<Record<string, unknown>> = [];
      for (const e of escs ?? []) {
        const doctorId = e.doctor_id as string;
        const { data: doctor } = await supabase
          .from("doctor_profiles")
          .select("full_name, specialty")
          .eq("doctor_id", doctorId)
          .maybeSingle();
        const { data: priorCalls } = await supabase
          .from("royal_checkin_escalation_calls")
          .select("call_id, patient_session_id, call_started_at, call_ended_at, duration_seconds, patient_accepted")
          .eq("escalation_id", e.escalation_id);

        out.push({
          ...e,
          doctor: doctor ?? null,
          calls: priorCalls ?? [],
        });
      }
      return successResponse({ escalations: out }, 200, origin);
    }

    // ── accept_ring ──────────────────────────────────────────────────────
    if (action === "accept_ring") {
      const { userId: coId } = await requireDoctorOrCO(req);
      const escalationId = body.escalation_id as string | undefined;
      if (!escalationId) throw new ValidationError("escalation_id is required");

      const { data: row } = await supabase
        .from("royal_checkin_escalations")
        .select("escalation_id, co_id, status, ring_expires_at")
        .eq("escalation_id", escalationId)
        .maybeSingle();
      if (!row) throw new ValidationError("Escalation not found");
      if (row.co_id !== coId) throw new ValidationError("You are not the CO rung for this escalation");
      if (row.status === "co_accepted" || row.status === "in_progress") {
        return successResponse({ ok: true, status: row.status }, 200, origin);
      }
      if (row.status !== "co_ringing") {
        return successResponse({ ok: false, reason: `status=${row.status}` }, 200, origin);
      }
      if (row.ring_expires_at && new Date(row.ring_expires_at as string) < new Date()) {
        return successResponse({ ok: false, reason: "ring_expired" }, 200, origin);
      }
      await supabase
        .from("royal_checkin_escalations")
        .update({ status: "co_accepted", co_accepted_at: new Date().toISOString() })
        .eq("escalation_id", escalationId);
      return successResponse({ ok: true }, 200, origin);
    }

    // ── decline_ring ─────────────────────────────────────────────────────
    if (action === "decline_ring") {
      const { userId: coId } = await requireDoctorOrCO(req);
      const escalationId = body.escalation_id as string | undefined;
      if (!escalationId) throw new ValidationError("escalation_id is required");

      const { data: row } = await supabase
        .from("royal_checkin_escalations")
        .select("escalation_id, co_id, status, reassign_count")
        .eq("escalation_id", escalationId)
        .maybeSingle();
      if (!row) throw new ValidationError("Escalation not found");
      if (row.co_id !== coId) throw new ValidationError("You are not the CO rung for this escalation");
      if (row.status !== "co_ringing") {
        return successResponse({ ok: true, already_settled: true }, 200, origin);
      }
      await supabase
        .from("royal_checkin_escalations")
        .update({
          status: "pending",
          co_id: null,
          ring_expires_at: null,
          co_notified_at: null,
          reassign_count: (row.reassign_count as number) + 1,
        })
        .eq("escalation_id", escalationId);
      return successResponse({ ok: true }, 200, origin);
    }

    // ── start_call ───────────────────────────────────────────────────────
    // The escalation row carries patient_session_id + consultation_id, so
    // the CO only sends escalation_id. Body may still include
    // patient_session_id for back-compat — it's verified to match.
    if (action === "start_call") {
      const { userId: coId } = await requireDoctorOrCO(req);
      const escalationId = body.escalation_id as string | undefined;
      if (!escalationId) throw new ValidationError("escalation_id is required");

      const { data: esc } = await supabase
        .from("royal_checkin_escalations")
        .select("escalation_id, co_id, doctor_id, status, patient_session_id, consultation_id")
        .eq("escalation_id", escalationId)
        .maybeSingle();
      if (!esc) throw new ValidationError("Escalation not found");
      if (esc.co_id !== coId) throw new ValidationError("You are not the CO for this escalation");
      if (!["co_accepted","in_progress"].includes(esc.status as string)) {
        throw new ValidationError(`Cannot start call from status ${esc.status}`);
      }
      const patientSessionId = esc.patient_session_id as string;
      const consultationId   = esc.consultation_id   as string | null;
      const bodyPatientId    = body.patient_session_id as string | undefined;
      if (bodyPatientId && bodyPatientId !== patientSessionId) {
        throw new ValidationError("patient_session_id does not match this escalation");
      }

      // Confirm the patient is still an active Royal client of the doctor
      // (a follow-up may have aged out between escalation creation and now).
      const { data: cons } = await supabase
        .from("consultations")
        .select("consultation_id")
        .eq("doctor_id", esc.doctor_id as string)
        .eq("patient_session_id", patientSessionId)
        .eq("service_tier", "ROYAL")
        .eq("status", "completed")
        .gt("follow_up_expiry", new Date().toISOString())
        .limit(1)
        .maybeSingle();
      const liveConsId = cons?.consultation_id as string | undefined;
      if (!liveConsId) throw new ValidationError("Patient is no longer an active Royal client of this doctor");

      const roomId = await createRoom();
      const { data: callRow, error: callErr } = await supabase
        .from("royal_checkin_escalation_calls")
        .insert({
          escalation_id: escalationId,
          patient_session_id: patientSessionId,
          consultation_id: liveConsId ?? consultationId,
          video_room_id: roomId,
          call_started_at: new Date().toISOString(),
        })
        .select("call_id")
        .single();
      if (callErr || !callRow) throw new Error(`Failed to insert call row: ${callErr?.message}`);

      await supabase
        .from("royal_checkin_escalations")
        .update({ status: "in_progress" })
        .eq("escalation_id", escalationId);

      const doctorName = await (async () => {
        const { data } = await supabase
          .from("doctor_profiles")
          .select("full_name")
          .eq("doctor_id", esc.doctor_id as string)
          .maybeSingle();
        const raw = (data?.full_name as string | null)?.trim();
        if (!raw) return "your doctor";
        return raw.replace(/^Dr\s+/i, "");
      })();

      // Fire-and-forget the patient incoming-call push so the CO's start_call
      // returns immediately and they can join the VideoSDK room without
      // waiting on the OAuth2/FCM round-trip.
      const pushPromise = pushIncomingCallToPatient({
        patientSessionId,
        consultationId: liveConsId,
        roomId,
        callId: callRow.call_id as string,
        doctorName,
      }).catch((e) => {
        console.error("[royal-cb] Failed to push patient incoming call:", e);
      });
      const er = (globalThis as unknown as { EdgeRuntime?: { waitUntil(p: Promise<unknown>): void } }).EdgeRuntime;
      if (er && typeof er.waitUntil === "function") er.waitUntil(pushPromise);

      return successResponse({
        ok: true,
        call_id: callRow.call_id,
        room_id: roomId,
        consultation_id: liveConsId,
        patient_session_id: patientSessionId,
      }, 200, origin);
    }

    // ── end_call ─────────────────────────────────────────────────────────
    if (action === "end_call") {
      const { userId: coId } = await requireDoctorOrCO(req);
      const callId = body.call_id as string | undefined;
      const durationSeconds = Number(body.duration_seconds ?? 0);
      const patientAccepted = Boolean(body.patient_accepted ?? false);
      if (!callId) throw new ValidationError("call_id is required");

      const { data: call } = await supabase
        .from("royal_checkin_escalation_calls")
        .select("call_id, escalation_id, call_ended_at")
        .eq("call_id", callId)
        .maybeSingle();
      if (!call) throw new ValidationError("Call not found");
      if (call.call_ended_at) {
        return successResponse({ ok: true, already_ended: true }, 200, origin);
      }

      // Confirm the CO owns the parent escalation.
      const { data: esc } = await supabase
        .from("royal_checkin_escalations")
        .select("co_id")
        .eq("escalation_id", call.escalation_id as string)
        .maybeSingle();
      if (!esc || esc.co_id !== coId) {
        throw new ValidationError("You are not the CO for this call");
      }

      await supabase
        .from("royal_checkin_escalation_calls")
        .update({
          call_ended_at: new Date().toISOString(),
          duration_seconds: Math.max(0, Math.floor(durationSeconds)),
          patient_accepted: patientAccepted,
        })
        .eq("call_id", callId);
      return successResponse({ ok: true }, 200, origin);
    }

    // ── complete_escalation ──────────────────────────────────────────────
    if (action === "complete_escalation") {
      const { userId: coId } = await requireDoctorOrCO(req);
      const escalationId = body.escalation_id as string | undefined;
      if (!escalationId) throw new ValidationError("escalation_id is required");

      const { data: esc } = await supabase
        .from("royal_checkin_escalations")
        .select("escalation_id, co_id, doctor_id, slot_date, slot_hour, reminder_id, status")
        .eq("escalation_id", escalationId)
        .maybeSingle();
      if (!esc) throw new ValidationError("Escalation not found");
      if (esc.co_id !== coId) throw new ValidationError("You are not the CO for this escalation");
      if (esc.status === "completed") {
        return successResponse({ ok: true, already_completed: true }, 200, origin);
      }

      // Credit earnings for each qualifying call that hasn't been credited yet.
      const { data: payable } = await supabase
        .from("royal_checkin_escalation_calls")
        .select("call_id, consultation_id")
        .eq("escalation_id", escalationId)
        .eq("qualifies_for_payment", true)
        .eq("earnings_credited", false);

      let credited = 0;
      for (const c of payable ?? []) {
        try {
          await supabase.from("doctor_earnings").insert({
            doctor_id: coId,
            consultation_id: c.consultation_id,
            amount: CO_PER_CALL_EARNING,
            status: "pending",
            earning_type: "royal_checkin_escalation",
            notes: `Royal check-in escalation. call_id=${c.call_id} escalation_id=${escalationId}`,
          });
          await supabase
            .from("royal_checkin_escalation_calls")
            .update({ earnings_credited: true })
            .eq("call_id", c.call_id);
          credited++;
        } catch (e) {
          console.warn(`[royal-cb] earnings insert failed for call ${c.call_id}:`, e);
        }
      }

      // Mark escalation completed, ack the doctor's reminder so the cron
      // stops running this slot, and set the doctor's warning_message —
      // existing trigger fn_increment_warning_count() bumps warning_count
      // and resets warning_acknowledged.
      const completedAt = new Date().toISOString();
      await supabase
        .from("royal_checkin_escalations")
        .update({ status: "completed", completed_at: completedAt })
        .eq("escalation_id", escalationId);

      await supabase
        .from("royal_checkin_reminders")
        .update({ acknowledged_at: completedAt, updated_at: completedAt })
        .eq("id", esc.reminder_id as string);

      const slotLabel = `${String(esc.slot_hour).padStart(2, "0")}:00`;
      const warningMsg =
        `Your ${slotLabel} Royal client check-in on ${esc.slot_date} was covered by a clinical officer ` +
        `because you didn't acknowledge any of the three reminders. Repeated misses may lead to ` +
        `account suspension or ban. Please ensure you check in on your Royal clients three times daily.`;
      await supabase
        .from("doctor_profiles")
        .update({ warning_message: warningMsg, warning_at: completedAt })
        .eq("doctor_id", esc.doctor_id as string);

      await logEvent({
        function_name: "royal-checkin-escalation-callback",
        level: "info",
        user_id: coId,
        action: "escalation_completed",
        metadata: {
          escalation_id: escalationId,
          doctor_id: esc.doctor_id,
          slot_hour: esc.slot_hour,
          slot_date: esc.slot_date,
          credited_calls: credited,
        },
      });

      return successResponse({ ok: true, credited_calls: credited }, 200, origin);
    }

    throw new ValidationError(
      "Invalid action. Use: list_active_escalations, accept_ring, decline_ring, start_call, end_call, complete_escalation",
    );
  } catch (err) {
    return errorResponse(err, origin);
  }
});
