// functions/generate-health-analytics/index.ts
// Generates AI-powered health analytics by region using patient session
// location data, consultation patterns, diagnoses, and prescriptions.
// Auth: admin, hr only (or X-Service-Key bypass for admin panel).
// Rate limit: LIMITS.payment (stricter).

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
    // Auth: X-Service-Key bypass (admin panel) or JWT auth
    let authUserId = "service-role";
    const serviceKey = req.headers.get("X-Service-Key");
    const expectedKey =
      Deno.env.get("INTERNAL_SERVICE_KEY") ??
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    const isServiceCall =
      serviceKey && expectedKey && serviceKey === expectedKey;

    if (!isServiceCall) {
      const auth = await validateAuth(req);
      requireRole(auth, "admin", "hr");
      authUserId = auth.userId ?? "unknown";
      await LIMITS.payment(authUserId);
    }

    const supabase = getServiceClient();

    // ── 1. Parallel queries ──────────────────────────────────
    const [consultationsRes, diagnosesRes, prescriptionsRes] =
      await Promise.all([
        supabase
          .from("consultations")
          .select(
            "consultation_id, patient_session_id, status, service_type, chief_complaint, consultation_type, created_at, patient_sessions(region, age, sex)"
          )
          .order("created_at", { ascending: false })
          .limit(3000),

        supabase
          .from("diagnoses")
          .select(
            "id, consultation_id, icd_code, description, severity, created_at"
          )
          .order("created_at", { ascending: false })
          .limit(3000),

        supabase
          .from("prescriptions")
          .select(
            "prescription_id, consultation_id, medication_name, created_at"
          )
          .order("created_at", { ascending: false })
          .limit(3000),
      ]);

    const consultations = consultationsRes.data ?? [];
    const diagnoses = diagnosesRes.data ?? [];
    const prescriptions = prescriptionsRes.data ?? [];

    const totalConsultations = consultations.length;
    const totalDiagnoses = diagnoses.length;
    const totalPrescriptions = prescriptions.length;

    // ── Empty data guard ─────────────────────────────────────
    if (totalConsultations === 0) {
      return successResponse(
        {
          generated_at: new Date().toISOString(),
          data_summary: {
            total_consultations: 0,
            total_diagnoses: 0,
            total_prescriptions: 0,
            regions_count: 0,
          },
          report: {
            executive_summary:
              "No consultation data is available yet. Health analytics will be generated once patients begin using the platform.",
            regional_hotspots: [],
            disease_patterns:
              "No diagnosis data available for analysis.",
            demographic_insights:
              "No demographic data available for analysis.",
            service_utilization:
              "No service utilization data available.",
            recommendations: [
              "Begin onboarding patients and doctors to generate health data for analytics.",
            ],
            data_quality_notes:
              "The platform currently has no consultation records. All analytics sections will populate as data grows.",
          },
        },
        200,
        origin
      );
    }

    // ── 2. Aggregate by region ───────────────────────────────

    interface RegionStats {
      consultations: number;
      byGender: Record<string, number>;
      byAgeGroup: Record<string, number>;
      byServiceType: Record<string, number>;
      byStatus: Record<string, number>;
      diagnoses: Record<string, number>;
      severityCounts: Record<string, number>;
      icdCodes: Record<string, number>;
      medications: Record<string, number>;
      prescriptionCount: number;
    }

    const regionStats: Record<string, RegionStats> = {};
    const consultationRegionMap: Record<string, string> = {};

    function getOrCreateRegion(region: string): RegionStats {
      if (!regionStats[region]) {
        regionStats[region] = {
          consultations: 0,
          byGender: {},
          byAgeGroup: {},
          byServiceType: {},
          byStatus: {},
          diagnoses: {},
          severityCounts: {},
          icdCodes: {},
          medications: {},
          prescriptionCount: 0,
        };
      }
      return regionStats[region];
    }

    // Aggregate consultations
    for (const c of consultations) {
      const ps = c.patient_sessions as unknown as {
        region: string | null;
        age: string | null;
        sex: string | null;
      } | null;

      const region = ps?.region ?? "Unknown";
      consultationRegionMap[c.consultation_id] = region;

      const rs = getOrCreateRegion(region);
      rs.consultations++;

      const sex = ps?.sex ?? "unknown";
      rs.byGender[sex] = (rs.byGender[sex] || 0) + 1;

      const age = ps?.age ?? "unknown";
      rs.byAgeGroup[age] = (rs.byAgeGroup[age] || 0) + 1;

      const svc = c.service_type ?? "unknown";
      rs.byServiceType[svc] = (rs.byServiceType[svc] || 0) + 1;

      const status = c.status ?? "unknown";
      rs.byStatus[status] = (rs.byStatus[status] || 0) + 1;
    }

    // Map diagnoses to regions
    for (const d of diagnoses) {
      const region =
        consultationRegionMap[d.consultation_id] ?? "Unknown";
      const rs = getOrCreateRegion(region);

      if (d.description) {
        rs.diagnoses[d.description] =
          (rs.diagnoses[d.description] || 0) + 1;
      }
      if (d.severity) {
        rs.severityCounts[d.severity] =
          (rs.severityCounts[d.severity] || 0) + 1;
      }
      if (d.icd_code) {
        rs.icdCodes[d.icd_code] =
          (rs.icdCodes[d.icd_code] || 0) + 1;
      }
    }

    // Map prescriptions to regions
    for (const p of prescriptions) {
      const region =
        consultationRegionMap[p.consultation_id] ?? "Unknown";
      const rs = getOrCreateRegion(region);
      rs.prescriptionCount++;
      if (p.medication_name) {
        rs.medications[p.medication_name] =
          (rs.medications[p.medication_name] || 0) + 1;
      }
    }

    const regionCount = Object.keys(regionStats).filter(
      (r) => r !== "Unknown"
    ).length;

    // ── 3. Build the ChatGPT prompt ─────────────────────────

    // Per-region summaries (top 15 by consultation volume)
    const regionSummaries = Object.entries(regionStats)
      .sort((a, b) => b[1].consultations - a[1].consultations)
      .slice(0, 15)
      .map(([region, stats]) => {
        const topDiagnoses = Object.entries(stats.diagnoses)
          .sort((a, b) => b[1] - a[1])
          .slice(0, 5)
          .map(([desc, count]) => `${desc} (${count})`)
          .join(", ");

        const genderBreakdown = Object.entries(stats.byGender)
          .map(([g, c]) => `${g}: ${c}`)
          .join(", ");

        const ageBreakdown = Object.entries(stats.byAgeGroup)
          .map(([a, c]) => `${a}: ${c}`)
          .join(", ");

        const severityBreakdown = Object.entries(stats.severityCounts)
          .map(([s, c]) => `${s}: ${c}`)
          .join(", ");

        const serviceBreakdown = Object.entries(stats.byServiceType)
          .map(([s, c]) => `${s}: ${c}`)
          .join(", ");

        const topMeds = Object.entries(stats.medications)
          .sort((a, b) => b[1] - a[1])
          .slice(0, 5)
          .map(([m, c]) => `${m} (${c})`)
          .join(", ");

        return `
REGION: ${region}
- Total consultations: ${stats.consultations}
- Gender: ${genderBreakdown || "N/A"}
- Age groups: ${ageBreakdown || "N/A"}
- Service types: ${serviceBreakdown || "N/A"}
- Top diagnoses: ${topDiagnoses || "None recorded"}
- Severity breakdown: ${severityBreakdown || "N/A"}
- Top medications prescribed: ${topMeds || "None recorded"}
- Prescriptions issued: ${stats.prescriptionCount}`;
      })
      .join("\n");

    // Global diagnosis ranking
    const globalDiagnoses: Record<string, number> = {};
    for (const d of diagnoses) {
      if (d.description) {
        globalDiagnoses[d.description] =
          (globalDiagnoses[d.description] || 0) + 1;
      }
    }
    const topGlobalDiagnoses = Object.entries(globalDiagnoses)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 10)
      .map(([desc, count]) => `${desc} (${count})`)
      .join(", ");

    const prompt = `
You are a public health analytics assistant for eSIRI Plus, a telemedicine platform operating in Tanzania. Analyze the following patient health data aggregated by region and generate a comprehensive health analytics report.

OVERVIEW:
- Total consultations analyzed: ${totalConsultations}
- Total diagnoses recorded: ${totalDiagnoses}
- Total prescriptions issued: ${totalPrescriptions}
- Regions with data: ${regionCount}
- Top 10 diagnoses (platform-wide): ${topGlobalDiagnoses || "None yet"}

REGIONAL DATA:
${regionSummaries}

Generate a professional health analytics report with these EXACT JSON keys:

1. "executive_summary" - 3-5 sentence overview of the platform's health landscape across Tanzania's regions. Highlight the most significant patterns.

2. "regional_hotspots" - Array of objects, each with: "region" (string), "concern" (string, 1-2 sentences describing the key health concern), "priority" (string: "high", "medium", or "low"). Include up to 5 regions with the most notable health patterns.

3. "disease_patterns" - 3-5 sentences analyzing the most common diagnoses, severity distribution, and any concerning patterns in disease spread across regions.

4. "demographic_insights" - 2-4 sentences about how health patterns vary by gender and age group across regions.

5. "service_utilization" - 2-3 sentences about which medical services are most in demand and whether the supply matches the need.

6. "recommendations" - Array of 4-6 actionable public health recommendations (strings), each 1-2 sentences. Focus on resource allocation, screening priorities, and regional health interventions.

7. "data_quality_notes" - 1-2 sentences about any data gaps or limitations observed (e.g. regions with too few cases for reliable conclusions, missing diagnoses).

Guidelines:
- Focus on PUBLIC HEALTH insights, not business metrics.
- Use epidemiological language where appropriate.
- Tanzania context: consider tropical diseases, regional healthcare access disparities, and common health challenges.
- Base analysis ONLY on the provided data. Do not fabricate numbers or trends.
- If data is insufficient for a section, say so explicitly rather than speculating.
Respond ONLY with valid JSON, no markdown, no backticks.
`;

    // ── 4. Call ChatGPT ──────────────────────────────────────

    const aiRes = await fetch(
      "https://api.openai.com/v1/chat/completions",
      {
        method: "POST",
        headers: {
          Authorization: `Bearer ${OPENAI_API_KEY}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          model: "gpt-4o-mini",
          response_format: { type: "json_object" },
          messages: [
            {
              role: "system",
              content:
                "You are a public health analytics AI for a telemedicine platform in Tanzania. Respond only with valid JSON. No preamble, no markdown.",
            },
            { role: "user", content: prompt },
          ],
          max_tokens: 3000,
          temperature: 0.3,
        }),
      }
    );

    if (!aiRes.ok) {
      const errText = await aiRes.text();
      console.error(
        `[health-analytics] OpenAI error (${aiRes.status}): ${errText}`
      );
      throw new Error("AI health analytics generation failed");
    }

    const aiData = await aiRes.json();
    let report: Record<string, unknown>;

    try {
      const rawText = aiData.choices[0].message.content as string;
      const cleaned = rawText.replace(/```json|```/g, "").trim();
      report = JSON.parse(cleaned);
    } catch {
      throw new Error("Failed to parse AI health analytics output");
    }

    // ── 5. Log event ─────────────────────────────────────────

    await logEvent({
      function_name: "generate-health-analytics",
      level: "info",
      user_id: authUserId,
      action: "health_analytics_generated",
      metadata: {
        total_consultations: totalConsultations,
        total_diagnoses: totalDiagnoses,
        regions_count: regionCount,
      },
      ip_address: getClientIp(req),
    });

    // ── 6. Return ────────────────────────────────────────────

    return successResponse(
      {
        generated_at: new Date().toISOString(),
        data_summary: {
          total_consultations: totalConsultations,
          total_diagnoses: totalDiagnoses,
          total_prescriptions: totalPrescriptions,
          regions_count: regionCount,
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
