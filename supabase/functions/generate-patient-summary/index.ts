// functions/generate-patient-summary/index.ts
// Generates an AI-powered patient medical summary.
// Aggregates all consultations, reports, prescriptions, and diagnoses
// for a patient session, then uses ChatGPT to produce a comprehensive summary.
// Auth: doctor only. Rate limit: LIMITS.payment (stricter).

import { handlePreflight } from "../_shared/cors.ts";
import { validateAuth, requireRole } from "../_shared/auth.ts";
import { errorResponse, successResponse, ValidationError } from "../_shared/errors.ts";
import { getServiceClient } from "../_shared/supabase.ts";
import { LIMITS } from "../_shared/rateLimit.ts";
import { logEvent, getClientIp } from "../_shared/logger.ts";

const OPENAI_API_KEY = Deno.env.get("OPENAI_API_KEY")!;

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    const auth = await validateAuth(req);
    requireRole(auth, "doctor");

    await LIMITS.payment(auth.userId!);

    const body = await req.json();
    const consultationId = body.consultation_id;
    if (typeof consultationId !== "string" || !consultationId.trim()) {
      throw new ValidationError("consultation_id is required");
    }

    const supabase = getServiceClient();

    // 1. Fetch the consultation (must belong to this doctor)
    const { data: consultation } = await supabase
      .from("consultations")
      .select("consultation_id, patient_session_id, doctor_id, service_type, chief_complaint, status, session_start_time, session_end_time")
      .eq("consultation_id", consultationId)
      .eq("doctor_id", auth.userId)
      .single();

    if (!consultation) {
      throw new ValidationError("Consultation not found or access denied");
    }

    const patientSessionId = consultation.patient_session_id;

    // 2. Fetch patient profile
    const { data: patientSession } = await supabase
      .from("patient_sessions")
      .select("session_id, full_name, age, gender, region")
      .eq("session_id", patientSessionId)
      .single();

    // 3. All consultations for this patient
    const { data: allConsultations } = await supabase
      .from("consultations")
      .select("consultation_id, service_type, chief_complaint, status, session_start_time, session_end_time, created_at")
      .eq("patient_session_id", patientSessionId)
      .order("created_at", { ascending: true });

    // 4. All consultation reports for this patient's consultations
    const consultationIds = (allConsultations ?? []).map((c: any) => c.consultation_id);
    let reports: any[] = [];
    if (consultationIds.length > 0) {
      const { data: reportData } = await supabase
        .from("consultation_reports")
        .select("consultation_id, diagnosed_problem, category, severity, treatment_plan, presenting_symptoms, follow_up_recommended, created_at")
        .in("consultation_id", consultationIds)
        .order("created_at", { ascending: true });
      reports = reportData ?? [];
    }

    // 5. Prescriptions for this patient's consultations
    let prescriptions: any[] = [];
    if (consultationIds.length > 0) {
      const { data: rxData } = await supabase
        .from("prescriptions")
        .select("consultation_id, medication_name, dosage, frequency, duration, instructions, created_at")
        .in("consultation_id", consultationIds)
        .order("created_at", { ascending: true });
      prescriptions = rxData ?? [];
    }

    // 6. Diagnoses
    let diagnoses: any[] = [];
    if (consultationIds.length > 0) {
      const { data: dxData } = await supabase
        .from("diagnoses")
        .select("consultation_id, diagnosis_name, diagnosis_code, notes, created_at")
        .in("consultation_id", consultationIds)
        .order("created_at", { ascending: true });
      diagnoses = dxData ?? [];
    }

    // 7. Vital signs
    let vitals: any[] = [];
    if (consultationIds.length > 0) {
      const { data: vitalData } = await supabase
        .from("vital_signs")
        .select("consultation_id, vital_type, value, unit, created_at")
        .in("consultation_id", consultationIds)
        .order("created_at", { ascending: true });
      vitals = vitalData ?? [];
    }

    // 8. Chat messages from the current consultation (for context)
    const { data: messages } = await supabase
      .from("messages")
      .select("sender_type, message_text, created_at")
      .eq("consultation_id", consultationId)
      .order("created_at", { ascending: true })
      .limit(80);

    const chatTranscript = (messages ?? [])
      .map((m: any) => `[${(m.sender_type ?? "").toUpperCase()}]: ${m.message_text}`)
      .join("\n");

    // 9. Medical records / attachments
    let attachments: any[] = [];
    if (consultationIds.length > 0) {
      const { data: attachData } = await supabase
        .from("attachments")
        .select("consultation_id, file_name, file_type, created_at")
        .in("consultation_id", consultationIds)
        .order("created_at", { ascending: true });
      attachments = attachData ?? [];
    }

    // Build the ChatGPT prompt
    const prompt = `
You are a professional medical documentation assistant. Generate a comprehensive patient medical summary based on the data below.

PATIENT INFORMATION:
- Name: ${patientSession?.full_name ?? "Anonymous Patient"}
- Age: ${patientSession?.age ?? "Unknown"}
- Gender: ${patientSession?.gender ?? "Unknown"}
- Region: ${patientSession?.region ?? "Unknown"}

CONSULTATION HISTORY (${(allConsultations ?? []).length} consultations):
${(allConsultations ?? []).map((c: any, i: number) => `
Consultation ${i + 1}:
  - Service: ${c.service_type}
  - Chief Complaint: ${c.chief_complaint || "Not recorded"}
  - Status: ${c.status}
  - Date: ${c.session_start_time ?? c.created_at}
`).join("") || "No consultations"}

CLINICAL REPORTS:
${reports.map((r: any) => `
  - Problem: ${r.diagnosed_problem}
  - Category: ${r.category}
  - Severity: ${r.severity}
  - Symptoms: ${r.presenting_symptoms || "N/A"}
  - Treatment: ${r.treatment_plan}
  - Follow-up: ${r.follow_up_recommended ? "Recommended" : "Not needed"}
`).join("") || "No reports available"}

DIAGNOSES:
${diagnoses.map((d: any) => `  - ${d.diagnosis_name}${d.diagnosis_code ? ` (${d.diagnosis_code})` : ""}${d.notes ? `: ${d.notes}` : ""}`).join("\n") || "No diagnoses recorded"}

PRESCRIPTIONS:
${prescriptions.map((p: any) => `  - ${p.medication_name}: ${p.dosage}, ${p.frequency}, ${p.duration}${p.instructions ? ` — ${p.instructions}` : ""}`).join("\n") || "No prescriptions"}

VITAL SIGNS:
${vitals.map((v: any) => `  - ${v.vital_type}: ${v.value} ${v.unit ?? ""}`).join("\n") || "No vital signs recorded"}

RECENT CONSULTATION CHAT TRANSCRIPT:
${chatTranscript || "No chat messages recorded"}

SHARED FILES/ATTACHMENTS:
${attachments.map((a: any) => `  - ${a.file_name} (${a.file_type})`).join("\n") || "None"}

Generate a comprehensive patient medical summary with these EXACT JSON keys:
1. "patient_overview" — Brief demographic and context overview. 2-3 sentences.
2. "medical_history" — Summary of all consultations, diagnoses, and presenting complaints in chronological order. 3-5 sentences.
3. "current_conditions" — Active conditions, latest diagnoses, and their severities. 2-4 sentences.
4. "treatment_summary" — Summary of all treatments, prescriptions, and care plans. 2-4 sentences.
5. "vital_signs_summary" — Summary of recorded vital signs and any notable trends. 1-3 sentences. "No vital signs recorded" if none.
6. "recommendations" — Clinical recommendations for continuity of care, follow-ups, or things to watch for. 2-4 sentences.

Use professional medical language suitable for a clinical summary. Do not fabricate information not present in the inputs.
Respond ONLY with valid JSON, no markdown, no backticks.
`;

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
            content: "You are a clinical documentation AI. Respond only with valid JSON. No preamble, no markdown.",
          },
          { role: "user", content: prompt },
        ],
        max_tokens: 2500,
        temperature: 0.3,
      }),
    });

    if (!aiRes.ok) {
      const errText = await aiRes.text();
      console.error(`[patient-summary] OpenAI error (${aiRes.status}): ${errText}`);
      throw new Error("AI summary generation failed");
    }

    const aiData = await aiRes.json();
    let summary: Record<string, string>;

    try {
      const rawText = aiData.choices[0].message.content as string;
      const cleaned = rawText.replace(/```json|```/g, "").trim();
      summary = JSON.parse(cleaned);
    } catch {
      throw new Error("Failed to parse AI summary output");
    }

    await logEvent({
      function_name: "generate-patient-summary",
      level: "info",
      user_id: auth.userId,
      action: "patient_summary_generated",
      metadata: {
        consultation_id: consultationId,
        patient_session_id: patientSessionId,
        consultations_count: (allConsultations ?? []).length,
      },
      ip_address: getClientIp(req),
    });

    return successResponse(
      {
        patient_name: patientSession?.full_name ?? "Anonymous Patient",
        patient_session_id: patientSessionId,
        generated_at: new Date().toISOString(),
        summary,
      },
      200,
      origin
    );
  } catch (err) {
    return errorResponse(err, origin);
  }
});
