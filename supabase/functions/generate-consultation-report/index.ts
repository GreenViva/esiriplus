// functions/generate-consultation-report/index.ts
// Generates an AI-powered consultation report using OpenAI ChatGPT.
// Accepts structured form data from the doctor, generates professional prose,
// stores the report, and notifies the patient.
// Only doctors can trigger this. Rate limit: 10/min.

import { handlePreflight } from "../_shared/cors.ts";
import { validateAuth, requireRole } from "../_shared/auth.ts";
import { LIMITS } from "../_shared/rateLimit.ts";
import { errorResponse, successResponse, ValidationError } from "../_shared/errors.ts";
import { logEvent, getClientIp } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

const OPENAI_API_KEY = Deno.env.get("OPENAI_API_KEY") ?? "";

interface PrescriptionItem {
  medication: string;
  form: string;
  dosage: string;
  route?: string; // "IM", "IV", or "SC" — only for injections
}

interface MedicationTimetableItem {
  medication_name: string;
  dosage?: string;
  form?: string;
  times_per_day: number;
  scheduled_times: string[]; // ["08:00", "14:00", "20:00"]
  duration_days: number;
}

interface ReportRequest {
  consultation_id: string;
  diagnosed_problem: string;
  category: string;
  severity: string;
  treatment_plan: string;
  further_notes?: string;
  follow_up_recommended: boolean;
  prescriptions?: PrescriptionItem[];
  medication_timetables?: MedicationTimetableItem[];
}

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    const auth = await validateAuth(req);
    requireRole(auth, "doctor");
    await LIMITS.payment(auth.userId!);

    const raw = await req.json();
    if (typeof raw?.consultation_id !== "string") {
      throw new ValidationError("consultation_id is required");
    }
    if (typeof raw?.diagnosed_problem !== "string" || !raw.diagnosed_problem.trim()) {
      throw new ValidationError("diagnosed_problem is required");
    }
    if (typeof raw?.category !== "string" || !raw.category.trim()) {
      throw new ValidationError("category is required");
    }
    if (typeof raw?.treatment_plan !== "string" || !raw.treatment_plan.trim()) {
      throw new ValidationError("treatment_plan is required");
    }

    const {
      consultation_id,
      patient_age,
      patient_gender,
      diagnosed_problem,
      category,
      severity,
      treatment_plan,
      further_notes,
      follow_up_recommended,
      prescriptions,
    } = raw as ReportRequest & { patient_age?: string; patient_gender?: string };

    const prescriptionsList = Array.isArray(prescriptions) ? prescriptions : [];

    // Fail fast if OpenAI key is not configured
    if (!OPENAI_API_KEY) {
      throw new ValidationError("OpenAI API key is not configured on the server");
    }

    const supabase = getServiceClient();

    // Fetch consultation details (must belong to this doctor)
    const { data: consultation, error: consultErr } = await supabase
      .from("consultations")
      .select("consultation_id, service_type, service_tier, chief_complaint, status, session_start_time, session_end_time, patient_session_id, follow_up_count")
      .eq("consultation_id", consultation_id)
      .eq("doctor_id", auth.userId)
      .single();

    if (consultErr || !consultation) {
      console.error("[generate-report] Consultation lookup failed:", consultErr?.message ?? "not found");
      throw new ValidationError("Consultation not found or access denied");
    }

    // Fetch doctor profile separately (doctor_profiles.doctor_id = auth.users.id)
    const { data: doctorProfile } = await supabase
      .from("doctor_profiles")
      .select("full_name, specialist_field")
      .eq("doctor_id", auth.userId)
      .single();

    // Check if report already exists — for reopened consultations (follow-ups),
    // delete the old report so a fresh one can be generated for this session.
    const { data: existingReport } = await supabase
      .from("consultation_reports")
      .select("report_id")
      .eq("consultation_id", consultation_id)
      .maybeSingle();

    if (existingReport) {
      // Check if consultation was reopened (follow_up_count > 0)
      if (consultation.follow_up_count > 0) {
        // Delete old report so the new one replaces it
        await supabase
          .from("consultation_reports")
          .delete()
          .eq("report_id", existingReport.report_id);
        console.log(`[generate-report] Deleted old report ${existingReport.report_id} for reopened consultation ${consultation_id}`);
      } else {
        return successResponse({
          message: "Report already exists",
          report_id: existingReport.report_id,
        }, 200, origin);
      }
    }

    // Fetch chat messages for context
    const { data: messages } = await supabase
      .from("messages")
      .select("sender_type, message_text, created_at")
      .eq("consultation_id", consultation_id)
      .order("created_at", { ascending: true })
      .limit(100);

    const chatTranscript = (messages ?? [])
      .map((m) => `[${m.sender_type.toUpperCase()}]: ${m.message_text}`)
      .join("\n");

    const doctorName = doctorProfile?.full_name ?? "Doctor";

    // Build the OpenAI prompt
    const prompt = `
You are a professional medical documentation assistant. Generate a structured telemedicine consultation report based on the doctor's clinical notes and the consultation chat transcript.

PATIENT DEMOGRAPHICS:
- Age: ${patient_age || "Not specified"}
- Gender: ${patient_gender || "Not specified"}

DOCTOR'S CLINICAL NOTES:
- Diagnosed Problem: ${diagnosed_problem}
- Category: ${category}
- Severity: ${severity || "Not specified"}
- Treatment Plan / Decision: ${treatment_plan}
- Prescribed Medications: ${prescriptionsList.length > 0 ? prescriptionsList.map((p) => `${p.medication} [${p.form}${p.route ? ` — ${p.route}` : ""}] (${p.dosage})`).join("; ") : "None"}
- Further Notes: ${further_notes || "None"}
- Follow-up Recommended: ${follow_up_recommended ? "Yes" : "No"}

CONSULTATION DETAILS:
- Service Type: ${consultation.service_type}
- Chief Complaint: ${consultation.chief_complaint || "Not recorded"}
- Session: ${consultation.session_start_time} to ${consultation.session_end_time ?? "ongoing"}

CHAT TRANSCRIPT:
${chatTranscript || "No chat messages recorded"}

Generate a professional medical report with these EXACT JSON keys:
1. "presenting_symptoms" — Expand the diagnosed problem and chat context into a professional summary of the patient's presenting symptoms and complaints. 2-4 sentences.
2. "diagnosis_assessment" — Professional clinical assessment based on the doctor's diagnosis, category, and severity. 2-4 sentences.
3. "treatment_plan_prose" — Expand the doctor's treatment plan into a professional recommendation. If medications were prescribed, incorporate them naturally into the treatment plan prose. 2-4 sentences.
4. "prescribed_medications_prose" — If medications were prescribed, write a professional medication section listing each medication with its dosage instructions. If no medications were prescribed, write "No medications prescribed for this consultation."
5. "follow_up_instructions" — Professional follow-up care instructions. If follow-up is recommended, include that. 2-3 sentences.

Use professional medical language suitable for a telemedicine consultation report. Do not fabricate information not present in the inputs.
Respond ONLY with valid JSON, no markdown, no backticks.
`;

    // Call OpenAI ChatGPT
    const aiRes = await fetch("https://api.openai.com/v1/chat/completions", {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${OPENAI_API_KEY}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        model: "gpt-4o-mini",
        response_format: { type: "json_object" },
        messages: [
          {
            role: "system",
            content: "You are a clinical documentation AI. Respond only with valid JSON. No preamble, no markdown, no backticks.",
          },
          { role: "user", content: prompt },
        ],
        max_tokens: 2000,
        temperature: 0.3,
      }),
    });

    if (!aiRes.ok) {
      const errText = await aiRes.text();
      console.error(`[generate-report] OpenAI error (${aiRes.status}): ${errText}`);
      throw new ValidationError(`AI report generation failed (HTTP ${aiRes.status})`);
    }

    const aiData = await aiRes.json();
    let reportContent: Record<string, string>;

    try {
      const rawText = aiData.choices[0].message.content as string;
      const cleaned = rawText.replace(/```json|```/g, "").trim();
      reportContent = JSON.parse(cleaned);
    } catch (parseErr) {
      console.error("[generate-report] AI response parse failed. Raw:", aiData?.choices?.[0]?.message?.content);
      throw new ValidationError("Failed to parse AI report output");
    }

    // Generate a verification code
    const verificationCode = crypto.randomUUID().replace(/-/g, "").toUpperCase().slice(0, 12);
    const consultationDate = consultation.session_start_time
      ? new Date(consultation.session_start_time).toISOString()
      : new Date().toISOString();

    // Save the full report
    // Original Lovable table has `diagnosis` (NOT NULL). Other columns added by migrations.
    const { data: report, error: reportErr } = await supabase
      .from("consultation_reports")
      .insert({
        consultation_id,
        doctor_id: auth.userId,
        diagnosis: diagnosed_problem,
        diagnosed_problem,
        patient_age: patient_age ?? null,
        patient_gender: patient_gender ?? null,
        category,
        severity: severity || "Mild",
        treatment_plan: reportContent.treatment_plan_prose ?? treatment_plan,
        further_notes: further_notes ?? null,
        follow_up_recommended: follow_up_recommended ?? false,
        presenting_symptoms: reportContent.presenting_symptoms ?? "",
        assessment: reportContent.diagnosis_assessment ?? "",
        follow_up_plan: reportContent.follow_up_instructions ?? "",
        history: prescriptionsList.length > 0
          ? prescriptionsList.map((p, i) => `${i + 1}. ${p.medication}${p.route ? ` [${p.route}]` : ""}\n   ${p.dosage}`).join("\n")
          : "",
        prescriptions: prescriptionsList,
        doctor_name: doctorName,
        patient_session_id: consultation.patient_session_id,
        consultation_date: consultationDate,
        verification_code: verificationCode,
        is_ai_generated: true,
        created_at: new Date().toISOString(),
      })
      .select("report_id")
      .single();

    if (reportErr) {
      console.error("[generate-report] Insert report error:", JSON.stringify(reportErr));
      throw new ValidationError(`Report save failed: ${reportErr.message}`);
    }

    // ── Normalised prescriptions ─────────────────────────────────────────
    // The full prescription list is stored as JSON on consultation_reports
    // for report rendering. We ALSO write one row per prescription into the
    // standalone prescriptions table so other surfaces (Royal Clients screen
    // medication-reminder picker, doctor's history view, etc.) can query a
    // clean relational list. Best-effort: any failure here just logs.
    if (prescriptionsList.length > 0) {
      const rxRows = prescriptionsList
        .filter((p) => typeof p.medication === "string" && p.medication.trim().length > 0)
        .map((p) => ({
          consultation_id,
          medication_name: p.medication,
          dosage: p.dosage ?? null,
          frequency: null as string | null,
          duration: null as string | null,
        }));
      if (rxRows.length > 0) {
        try {
          const { error: rxErr } = await supabase.from("prescriptions").insert(rxRows);
          if (rxErr) {
            console.warn(`[generate-report] prescriptions insert warning: ${rxErr.message}`);
          } else {
            console.log(`[generate-report] Inserted ${rxRows.length} prescriptions for ${consultation_id}`);
          }
        } catch (e) {
          console.warn(`[generate-report] prescriptions insert exception:`, e);
        }
      }
    }

    // Mark report as submitted AND ensure consultation is completed — this
    // triggers fn_sync_doctor_in_session() which clears in_session, making
    // the doctor available for new requests. Setting status alongside
    // report_submitted is a belt-and-suspenders guarantee: even if the
    // consultation was not already marked completed (e.g. the client
    // disconnected before end_consultation RPC ran), submitting the report
    // always releases the doctor from in_session.
    const terminalStatuses = ["completed", "cancelled", "timeout"];
    const nextStatus = terminalStatuses.includes((consultation.status ?? "").toLowerCase())
      ? consultation.status
      : "completed";
    await supabase
      .from("consultations")
      .update({ status: nextStatus, report_submitted: true })
      .eq("consultation_id", consultation_id);

    // ── Medication timetables (ROYAL only) ───────────────────────────────
    // Doctor can set nurse-assisted medication reminder schedules for
    // Royal-tier patients. Each timetable entry triggers a cron job that
    // auto-assigns a nurse to call the patient at the scheduled times.
    const tier = (consultation.service_tier ?? "ECONOMY").toUpperCase();
    if (tier === "ROYAL" && Array.isArray(raw.medication_timetables) && raw.medication_timetables.length > 0) {
      const today = new Date().toISOString().split("T")[0];
      for (const tt of raw.medication_timetables as MedicationTimetableItem[]) {
        if (!tt.medication_name || !tt.scheduled_times?.length || !tt.duration_days) continue;
        const endDate = new Date(Date.now() + (tt.duration_days - 1) * 86400000).toISOString().split("T")[0];
        try {
          await supabase.from("medication_timetables").insert({
            consultation_id,
            patient_session_id: consultation.patient_session_id,
            doctor_id: auth.userId,
            medication_name: tt.medication_name,
            dosage: tt.dosage ?? null,
            form: tt.form ?? "Tablets",
            times_per_day: tt.times_per_day,
            scheduled_times: tt.scheduled_times,
            duration_days: tt.duration_days,
            start_date: today,
            end_date: endDate,
          });
          console.log(`[generate-report] Created medication timetable: ${tt.medication_name} ${tt.times_per_day}x/day for ${tt.duration_days} days`);
        } catch (e) {
          console.error(`[generate-report] Failed to create timetable for ${tt.medication_name}:`, e);
        }
      }
    }

    // Send push notification to patient
    const serviceKey = Deno.env.get("INTERNAL_SERVICE_KEY") ?? Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;

    try {
      await fetch(`${supabaseUrl}/functions/v1/send-push-notification`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${serviceKey}`,
          "X-Service-Key": serviceKey,
        },
        body: JSON.stringify({
          session_id: consultation.patient_session_id,
          title: "Consultation Report Ready",
          body: `Dr. ${doctorName} has submitted your consultation report.`,
          type: "REPORT_READY",
          data: {
            consultation_id,
            report_id: report.report_id,
          },
        }),
      });
    } catch (err) {
      console.error("[generate-report] Notification failed:", err);
    }

    await logEvent({
      function_name: "generate-consultation-report",
      level: "info",
      user_id: auth.userId,
      action: "report_generated",
      metadata: {
        consultation_id,
        report_id: report.report_id,
        is_ai_generated: true,
        category,
        severity,
      },
      ip_address: getClientIp(req),
    });

    return successResponse({
      message: "Report generated successfully",
      report_id: report.report_id,
      verification_code: verificationCode,
      report: reportContent,
    }, 201, origin);

  } catch (err) {
    const errMsg = err instanceof Error ? err.message : String(err);
    const errStack = err instanceof Error ? err.stack : undefined;
    console.error(`[generate-report] CAUGHT ERROR: ${errMsg}`, errStack ?? "");
    try {
      await logEvent({
        function_name: "generate-consultation-report",
        level: "error",
        action: "report_generation_failed",
        error_message: errMsg,
      });
    } catch (logErr) {
      console.error("[generate-report] logEvent also failed:", logErr);
    }
    return errorResponse(err, origin);
  }
});
