// functions/generate-analytics-report/index.ts
// Generates an AI-powered platform analytics report.
// Aggregates platform-wide metrics (consultations, payments, doctors,
// patients, ratings) and uses ChatGPT to produce an analytics narrative.
// Auth: admin, hr only. Rate limit: LIMITS.payment (stricter).

import { handlePreflight } from "../_shared/cors.ts";
import { validateAuth, requireRole } from "../_shared/auth.ts";
import { errorResponse, successResponse } from "../_shared/errors.ts";
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
    requireRole(auth, "admin", "hr");

    await LIMITS.payment(auth.userId!);

    const supabase = getServiceClient();

    // 1. Doctor statistics (full breakdown)
    const { data: allDoctors } = await supabase
      .from("doctor_profiles")
      .select("doctor_id, is_verified, is_available, suspended_until, created_at");

    const totalDoctors = (allDoctors ?? []).length;
    const verifiedDoctors = (allDoctors ?? []).filter((d: any) => d.is_verified).length;
    const availableDoctors = (allDoctors ?? []).filter((d: any) => d.is_available).length;
    const suspendedDoctors = (allDoctors ?? []).filter((d: any) =>
      d.suspended_until && new Date(d.suspended_until) > new Date()
    ).length;
    const unverifiedDoctors = (allDoctors ?? []).filter((d: any) => !d.is_verified).length;

    // Doctors registered in last 30 days
    const thirtyDaysAgo = new Date(Date.now() - 30 * 86400000).toISOString();
    const newDoctorsLast30 = (allDoctors ?? []).filter(
      (d: any) => d.created_at && d.created_at >= thirtyDaysAgo
    ).length;

    // 2. Patient statistics
    const { data: allPatientSessions } = await supabase
      .from("patient_sessions")
      .select("session_id, created_at");

    const totalPatients = (allPatientSessions ?? []).length;
    const newPatientsLast30 = (allPatientSessions ?? []).filter(
      (p: any) => p.created_at && p.created_at >= thirtyDaysAgo
    ).length;

    // 3. Consultation statistics
    const { count: totalConsultations } = await supabase
      .from("consultations")
      .select("consultation_id", { count: "exact", head: true });

    const { count: completedConsultations } = await supabase
      .from("consultations")
      .select("consultation_id", { count: "exact", head: true })
      .eq("status", "completed");

    const { count: activeConsultations } = await supabase
      .from("consultations")
      .select("consultation_id", { count: "exact", head: true })
      .or("status.eq.active,status.eq.in_progress");

    const { count: cancelledConsultations } = await supabase
      .from("consultations")
      .select("consultation_id", { count: "exact", head: true })
      .eq("status", "cancelled");

    // Consultations by service type
    const { data: byServiceType } = await supabase
      .from("consultations")
      .select("service_type");

    const serviceTypeCounts: Record<string, number> = {};
    for (const c of byServiceType ?? []) {
      const st = c.service_type ?? "unknown";
      serviceTypeCounts[st] = (serviceTypeCounts[st] || 0) + 1;
    }

    // 4. Payment/revenue statistics
    const { data: payments } = await supabase
      .from("payments")
      .select("amount, currency, status");

    let totalRevenue = 0;
    let completedPayments = 0;
    let pendingPayments = 0;
    const currency = payments?.[0]?.currency ?? "TZS";

    for (const p of payments ?? []) {
      if (p.status === "completed" || p.status === "paid") {
        totalRevenue += Number(p.amount) || 0;
        completedPayments++;
      } else if (p.status === "pending") {
        pendingPayments++;
      }
    }

    // 5. Rating statistics
    const { data: allRatings } = await supabase
      .from("doctor_ratings")
      .select("rating, is_flagged");

    const ratingBreakdown = [0, 0, 0, 0, 0];
    let totalFlagged = 0;
    let ratingSum = 0;

    for (const r of allRatings ?? []) {
      if (r.rating >= 1 && r.rating <= 5) ratingBreakdown[r.rating - 1]++;
      ratingSum += r.rating;
      if (r.is_flagged) totalFlagged++;
    }

    const avgRating = (allRatings ?? []).length > 0
      ? (ratingSum / (allRatings ?? []).length).toFixed(1)
      : "N/A";

    // 6. Reports generated
    const { count: totalReports } = await supabase
      .from("consultation_reports")
      .select("report_id", { count: "exact", head: true });

    // 7. Prescriptions issued
    const { count: totalPrescriptions } = await supabase
      .from("prescriptions")
      .select("prescription_id", { count: "exact", head: true });

    // 8. Video calls
    const { count: totalVideoCalls } = await supabase
      .from("video_calls")
      .select("call_id", { count: "exact", head: true });

    // 9. Average consultation duration
    const { data: completedWithTimes } = await supabase
      .from("consultations")
      .select("session_start_time, session_end_time")
      .eq("status", "completed")
      .not("session_start_time", "is", null)
      .not("session_end_time", "is", null)
      .limit(500);

    let avgConsultDurationMin = 0;
    const durations: number[] = [];
    for (const c of completedWithTimes ?? []) {
      const start = new Date(c.session_start_time).getTime();
      const end = new Date(c.session_end_time).getTime();
      if (end > start) durations.push((end - start) / 60000);
    }
    if (durations.length > 0) {
      avgConsultDurationMin = Math.round(durations.reduce((a, b) => a + b, 0) / durations.length);
    }

    // 10. Doctor earnings total
    const { data: allEarnings } = await supabase
      .from("doctor_earnings")
      .select("amount");

    const totalDoctorEarnings = (allEarnings ?? []).reduce(
      (sum: number, e: any) => sum + (Number(e.amount) || 0), 0
    );

    // Build the ChatGPT prompt
    const prompt = `
You are a professional business analytics assistant for a telemedicine platform called eSIRI Plus operating in Tanzania. Generate a comprehensive platform analytics report based on the data below.

DOCTOR METRICS:
- Total Doctors Registered: ${totalDoctors}
- Verified Doctors: ${verifiedDoctors}
- Unverified/Pending: ${unverifiedDoctors}
- Currently Available: ${availableDoctors}
- Currently Suspended: ${suspendedDoctors}
- New Registrations (Last 30 Days): ${newDoctorsLast30}

PATIENT METRICS:
- Total Patient Sessions: ${totalPatients}
- New Patients (Last 30 Days): ${newPatientsLast30}

CONSULTATION METRICS:
- Total Consultations: ${totalConsultations ?? 0}
- Completed: ${completedConsultations ?? 0}
- Active/In Progress: ${activeConsultations ?? 0}
- Cancelled: ${cancelledConsultations ?? 0}
- Completion Rate: ${totalConsultations ? Math.round(((completedConsultations ?? 0) / (totalConsultations as number)) * 100) : 0}%
- Average Consultation Duration: ${avgConsultDurationMin > 0 ? `${avgConsultDurationMin} minutes` : "Not enough data"}
- By Service Type: ${Object.entries(serviceTypeCounts).map(([k, v]) => `${k}=${v}`).join(", ") || "N/A"}
- Total Video/Voice Calls: ${totalVideoCalls ?? 0}

REVENUE METRICS:
- Total Platform Revenue (Payments): ${totalRevenue.toLocaleString()} ${currency}
- Total Doctor Earnings Disbursed: ${totalDoctorEarnings.toLocaleString()} ${currency}
- Completed Payments: ${completedPayments}
- Pending Payments: ${pendingPayments}

PATIENT SATISFACTION:
- Total Ratings: ${(allRatings ?? []).length}
- Average Rating: ${avgRating}/5
- Rating Breakdown: 1★=${ratingBreakdown[0]}, 2★=${ratingBreakdown[1]}, 3★=${ratingBreakdown[2]}, 4★=${ratingBreakdown[3]}, 5★=${ratingBreakdown[4]}
- Flagged Reviews: ${totalFlagged}

CLINICAL OUTPUT:
- Consultation Reports Generated: ${totalReports ?? 0}
- Total Prescriptions Issued: ${totalPrescriptions ?? 0}

Generate a professional analytics report with these EXACT JSON keys:
1. "executive_summary" — 3-5 sentence high-level overview of platform performance, key highlights, and areas needing attention.
2. "doctor_workforce" — Analysis of doctor supply, verification rate, availability. 2-4 sentences.
3. "consultation_performance" — Analysis of consultation volumes, completion rates, and service type distribution. 2-4 sentences.
4. "revenue_analysis" — Revenue performance, payment completion, and financial health. 2-4 sentences.
5. "patient_satisfaction" — Patient rating trends, satisfaction levels, flagged concerns. 2-4 sentences.
6. "growth_opportunities" — 3-5 actionable recommendations for platform growth and improvement.
7. "risk_areas" — Key risks or concerns that need attention. 2-3 sentences.

Use professional business analytics language. Base analysis ONLY on the provided data. Do not fabricate trends or numbers.
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
            content: "You are a business analytics AI for a telemedicine platform. Respond only with valid JSON. No preamble, no markdown.",
          },
          { role: "user", content: prompt },
        ],
        max_tokens: 2500,
        temperature: 0.3,
      }),
    });

    if (!aiRes.ok) {
      const errText = await aiRes.text();
      console.error(`[analytics-report] OpenAI error (${aiRes.status}): ${errText}`);
      throw new Error("AI report generation failed");
    }

    const aiData = await aiRes.json();
    let report: Record<string, string>;

    try {
      const rawText = aiData.choices[0].message.content as string;
      const cleaned = rawText.replace(/```json|```/g, "").trim();
      report = JSON.parse(cleaned);
    } catch {
      throw new Error("Failed to parse AI report output");
    }

    await logEvent({
      function_name: "generate-analytics-report",
      level: "info",
      user_id: auth.userId,
      action: "analytics_report_generated",
      metadata: {
        total_doctors: totalDoctors,
        total_consultations: totalConsultations,
        total_revenue: totalRevenue,
      },
      ip_address: getClientIp(req),
    });

    return successResponse(
      {
        generated_at: new Date().toISOString(),
        metrics: {
          doctors: { total: totalDoctors, verified: verifiedDoctors, available: availableDoctors, suspended: suspendedDoctors, unverified: unverifiedDoctors, new_last_30d: newDoctorsLast30 },
          patients: { total: totalPatients, new_last_30d: newPatientsLast30 },
          consultations: {
            total: totalConsultations,
            completed: completedConsultations,
            active: activeConsultations,
            cancelled: cancelledConsultations,
            avg_duration_min: avgConsultDurationMin,
            video_calls: totalVideoCalls,
            by_service_type: serviceTypeCounts,
          },
          revenue: { total: totalRevenue, doctor_earnings: totalDoctorEarnings, currency, completed_payments: completedPayments, pending_payments: pendingPayments },
          ratings: {
            total: (allRatings ?? []).length,
            average: avgRating,
            breakdown: ratingBreakdown,
            flagged: totalFlagged,
          },
          reports_generated: totalReports,
          prescriptions_issued: totalPrescriptions,
        },
        report,
      },
      200,
      origin
    );
  } catch (err) {
    return errorResponse(err, origin);
  }
});
