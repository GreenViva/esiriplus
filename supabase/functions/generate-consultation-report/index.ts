// functions/generate-consultation-report/index.ts
// Generates an AI-powered consultation report after a completed session.
// Only doctors can trigger this. Rate limit: 10/min.

import { handlePreflight } from "../_shared/cors.ts";
import { validateAuth, requireRole } from "../_shared/auth.ts";
import { LIMITS } from "../_shared/rateLimit.ts";
import { errorResponse, successResponse, ValidationError } from "../_shared/errors.ts";
import { logEvent, getClientIp } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

const ANTHROPIC_API_KEY = Deno.env.get("ANTHROPIC_API_KEY")!;

interface ReportRequest {
  consultation_id: string;
  additional_notes?: string;
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

    const { consultation_id, additional_notes } = raw as ReportRequest;
    const supabase = getServiceClient();

    // Fetch consultation details (must belong to this doctor)
    const { data: consultation } = await supabase
      .from("consultations")
      .select(`
        consultation_id, service_type, chief_complaint, status,
        started_at, ended_at,
        doctor_profiles (full_name, specialization)
      `)
      .eq("consultation_id", consultation_id)
      .eq("doctor_id", auth.userId)
      .single();

    if (!consultation) {
      throw new ValidationError("Consultation not found or access denied");
    }

    // Check if report already exists
    const { data: existingReport } = await supabase
      .from("consultation_reports")
      .select("report_id, report_url")
      .eq("consultation_id", consultation_id)
      .single();

    if (existingReport) {
      return successResponse({
        message: "Report already exists",
        report_id: existingReport.report_id,
        report_url: existingReport.report_url,
      }, 200, origin);
    }

    // Fetch chat messages for context
    const { data: messages } = await supabase
      .from("messages")
      .select("sender_role, content, created_at")
      .eq("consultation_id", consultation_id)
      .order("created_at", { ascending: true })
      .limit(100);

    // Build prompt context
    const chatTranscript = (messages ?? [])
      .map((m) => `[${m.sender_role.toUpperCase()}]: ${m.content}`)
      .join("\n");

    const doctorName = (consultation as unknown as Record<string, unknown>)
      ?.doctor_profiles?.toString() ?? "Doctor";

    const prompt = `
You are a medical documentation assistant. Generate a structured clinical consultation report.

CONSULTATION DETAILS:
- Service Type: ${consultation.service_type}
- Chief Complaint: ${consultation.chief_complaint}
- Duration: ${consultation.started_at} to ${consultation.ended_at ?? "ongoing"}

CHAT TRANSCRIPT:
${chatTranscript || "No chat messages recorded"}

${additional_notes ? `ADDITIONAL DOCTOR NOTES:\n${additional_notes}` : ""}

Generate a structured report with:
1. Chief Complaint
2. History of Present Illness
3. Assessment
4. Plan / Recommendations
5. Follow-up Instructions

Use professional medical language. Do not include diagnoses outside the scope of the consultation.
Format as structured JSON with these exact keys: chief_complaint, history, assessment, plan, follow_up.
`;

    // Call Claude (Anthropic)
    const aiRes = await fetch("https://api.anthropic.com/v1/messages", {
      method: "POST",
      headers: {
        "x-api-key": ANTHROPIC_API_KEY,
        "anthropic-version": "2023-06-01",
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        model: "claude-haiku-4-5-20251001",
        max_tokens: 1500,
        system: "You are a clinical documentation AI. Respond only with valid JSON. No preamble, no markdown, no backticks.",
        messages: [
          { role: "user", content: prompt },
        ],
      }),
    });

    if (!aiRes.ok) throw new Error("AI report generation failed");

    const aiData = await aiRes.json();
    let reportContent: Record<string, string>;

    try {
      const raw = aiData.content[0].text as string;
      const cleaned = raw.replace(/```json|```/g, "").trim();
      reportContent = JSON.parse(cleaned);
    } catch {
      throw new Error("Failed to parse AI report output");
    }

    // Generate a QR verification code
    const verificationCode = crypto.randomUUID().replace(/-/g, "").toUpperCase().slice(0, 12);

    // Save the report
    const { data: report, error: reportErr } = await supabase
      .from("consultation_reports")
      .insert({
        consultation_id,
        doctor_id: auth.userId,
        chief_complaint: reportContent.chief_complaint,
        history: reportContent.history,
        assessment: reportContent.assessment,
        plan: reportContent.plan,
        follow_up: reportContent.follow_up,
        additional_notes: additional_notes ?? null,
        verification_code: verificationCode,
        is_ai_generated: true,
        created_at: new Date().toISOString(),
      })
      .select("report_id")
      .single();

    if (reportErr) throw reportErr;

    await logEvent({
      function_name: "generate-consultation-report",
      level: "info",
      user_id: auth.userId,
      action: "report_generated",
      metadata: {
        consultation_id,
        report_id: report.report_id,
        is_ai_generated: true,
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
    await logEvent({
      function_name: "generate-consultation-report",
      level: "error",
      action: "report_generation_failed",
      error_message: err instanceof Error ? err.message : String(err),
    });
    return errorResponse(err, origin);
  }
});
