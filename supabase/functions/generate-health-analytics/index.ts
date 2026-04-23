// functions/generate-health-analytics/index.ts
// Deep public-health analytics using every data source the admin panel
// surfaces: consultations, diagnoses, prescriptions, patient_sessions
// (independent of consultations, so coverage-only scenarios work),
// consultation_reports, doctor_ratings, and location_offer_redemptions.
//
// Uses OpenAI Structured Outputs (response_format: json_schema, strict: true)
// so the returned report always matches HealthAnalyticsReport exactly — no
// post-hoc parsing/repair.
//
// Auth: admin, hr only (or X-Service-Key bypass for admin panel).
// Rate limit: LIMITS.payment (stricter).

import { handlePreflight } from "../_shared/cors.ts";
import { validateAuth, requireRole } from "../_shared/auth.ts";
import { errorResponse, successResponse } from "../_shared/errors.ts";
import { getServiceClient } from "../_shared/supabase.ts";
import { LIMITS } from "../_shared/rateLimit.ts";
import { logEvent, getClientIp } from "../_shared/logger.ts";

const OPENAI_API_KEY = Deno.env.get("OPENAI_API_KEY")!;
const MODEL = "gpt-4o";                  // structured-output-capable
const MAX_TOKENS = 6000;

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

    // ── 1. Parallel data pull ────────────────────────────────
    const [
      consultationsRes,
      diagnosesRes,
      prescriptionsRes,
      patientSessionsRes,
      reportsRes,
      ratingsRes,
      redemptionsRes,
      offersRes,
    ] = await Promise.all([
      supabase
        .from("consultations")
        .select(
          "consultation_id, patient_session_id, doctor_id, status, service_type, service_tier, service_region, service_district, service_ward, chief_complaint, consultation_type, created_at, patient_sessions(region, age, sex)",
        )
        .order("created_at", { ascending: false })
        .limit(5000),

      supabase
        .from("diagnoses")
        .select("id, consultation_id, icd_code, description, severity, created_at")
        .order("created_at", { ascending: false })
        .limit(5000),

      supabase
        .from("prescriptions")
        .select("prescription_id, consultation_id, medication_name, created_at")
        .order("created_at", { ascending: false })
        .limit(5000),

      supabase
        .from("patient_sessions")
        .select("session_id, region, service_district, service_ward, age, sex, created_at")
        .order("created_at", { ascending: false })
        .limit(5000),

      supabase
        .from("consultation_reports")
        .select("report_id, consultation_id, diagnosis, category, severity, created_at")
        .order("created_at", { ascending: false })
        .limit(5000),

      supabase
        .from("doctor_ratings")
        .select("rating_id, doctor_id, consultation_id, rating, is_flagged, created_at")
        .order("created_at", { ascending: false })
        .limit(5000),

      supabase
        .from("location_offer_redemptions")
        .select("offer_id, consultation_id, original_price, discounted_price, redeemed_at")
        .order("redeemed_at", { ascending: false })
        .limit(5000),

      supabase
        .from("location_offers")
        .select("offer_id, title, region, district, ward, street, discount_type, discount_value, is_active, created_at"),
    ]);

    const consultations = consultationsRes.data ?? [];
    const diagnoses = diagnosesRes.data ?? [];
    const prescriptions = prescriptionsRes.data ?? [];
    const patientSessions = patientSessionsRes.data ?? [];
    const reports = reportsRes.data ?? [];
    const ratings = ratingsRes.data ?? [];
    const redemptions = redemptionsRes.data ?? [];
    const offers = offersRes.data ?? [];

    // ── 2. Aggregates ────────────────────────────────────────
    interface RegionStats {
      consultations: number;
      registrations: number; // patients whose region matches
      byGender: Record<string, number>;
      byAgeGroup: Record<string, number>;
      byServiceType: Record<string, number>;
      byStatus: Record<string, number>;
      byDistrict: Record<string, number>;
      diagnoses: Record<string, number>;
      severityCounts: Record<string, number>;
      icdCodes: Record<string, number>;
      medications: Record<string, number>;
      prescriptionCount: number;
      reportsCount: number;
      ratings: number[]; // array of star ratings
      flaggedRatings: number;
      offerRedemptions: number;
      offerSubsidyTzs: number;
    }

    const regionStats: Record<string, RegionStats> = {};
    const consultationRegionMap: Record<string, string> = {};

    function keyRegion(raw: string | null | undefined): string {
      const r = (raw ?? "").trim();
      if (!r) return "Unknown";
      if (r.toUpperCase() === "TANZANIA") return "Unknown"; // sentinel
      if (r.includes(",")) return "Unknown"; // legacy comma-blob
      return r;
    }

    function getOrCreateRegion(region: string): RegionStats {
      if (!regionStats[region]) {
        regionStats[region] = {
          consultations: 0,
          registrations: 0,
          byGender: {},
          byAgeGroup: {},
          byServiceType: {},
          byStatus: {},
          byDistrict: {},
          diagnoses: {},
          severityCounts: {},
          icdCodes: {},
          medications: {},
          prescriptionCount: 0,
          reportsCount: 0,
          ratings: [],
          flaggedRatings: 0,
          offerRedemptions: 0,
          offerSubsidyTzs: 0,
        };
      }
      return regionStats[region]!;
    }

    // Patient sessions — registrations per region
    for (const s of patientSessions) {
      const region = keyRegion(s.region);
      const rs = getOrCreateRegion(region);
      rs.registrations++;
      if (s.service_district?.trim()) {
        rs.byDistrict[s.service_district.trim()] =
          (rs.byDistrict[s.service_district.trim()] || 0) + 1;
      }
    }

    // Consultations
    for (const c of consultations) {
      const ps = c.patient_sessions as unknown as {
        region: string | null; age: string | null; sex: string | null;
      } | null;
      const region = keyRegion(c.service_region ?? ps?.region);
      consultationRegionMap[c.consultation_id] = region;

      const rs = getOrCreateRegion(region);
      rs.consultations++;
      rs.byGender[ps?.sex ?? "unknown"] = (rs.byGender[ps?.sex ?? "unknown"] || 0) + 1;
      rs.byAgeGroup[ps?.age ?? "unknown"] = (rs.byAgeGroup[ps?.age ?? "unknown"] || 0) + 1;
      rs.byServiceType[c.service_type ?? "unknown"] =
        (rs.byServiceType[c.service_type ?? "unknown"] || 0) + 1;
      rs.byStatus[c.status ?? "unknown"] = (rs.byStatus[c.status ?? "unknown"] || 0) + 1;
      if (c.service_district?.trim()) {
        rs.byDistrict[c.service_district.trim()] =
          (rs.byDistrict[c.service_district.trim()] || 0) + 1;
      }
    }

    // Diagnoses
    for (const d of diagnoses) {
      const region = consultationRegionMap[d.consultation_id] ?? "Unknown";
      const rs = getOrCreateRegion(region);
      if (d.description) rs.diagnoses[d.description] = (rs.diagnoses[d.description] || 0) + 1;
      if (d.severity)    rs.severityCounts[d.severity] = (rs.severityCounts[d.severity] || 0) + 1;
      if (d.icd_code)    rs.icdCodes[d.icd_code] = (rs.icdCodes[d.icd_code] || 0) + 1;
    }

    // Prescriptions
    for (const p of prescriptions) {
      const region = consultationRegionMap[p.consultation_id] ?? "Unknown";
      const rs = getOrCreateRegion(region);
      rs.prescriptionCount++;
      if (p.medication_name)
        rs.medications[p.medication_name] = (rs.medications[p.medication_name] || 0) + 1;
    }

    // Reports (pulled from consultation_reports)
    for (const r of reports) {
      const region = consultationRegionMap[r.consultation_id] ?? "Unknown";
      getOrCreateRegion(region).reportsCount++;
    }

    // Ratings — per-region via consultation link
    for (const rt of ratings) {
      const region = consultationRegionMap[rt.consultation_id] ?? "Unknown";
      const rs = getOrCreateRegion(region);
      rs.ratings.push(rt.rating);
      if (rt.is_flagged) rs.flaggedRatings++;
    }

    // Offer redemptions
    for (const red of redemptions) {
      const region = consultationRegionMap[red.consultation_id] ?? "Unknown";
      const rs = getOrCreateRegion(region);
      rs.offerRedemptions++;
      rs.offerSubsidyTzs += Math.max(0, (red.original_price ?? 0) - (red.discounted_price ?? 0));
    }

    // ── 3. Weekly trend data ─────────────────────────────────
    const weekStartIso = (iso: string): string => {
      const d = new Date(iso);
      const day = d.getUTCDay();
      const diff = d.getUTCDate() - day + (day === 0 ? -6 : 1);
      return new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), diff))
        .toISOString().slice(0, 10);
    };
    const weekMap: Record<
      string,
      { consultations: number; registrations: number; reports: number }
    > = {};
    for (const c of consultations) {
      const w = weekStartIso(c.created_at);
      (weekMap[w] ??= { consultations: 0, registrations: 0, reports: 0 }).consultations++;
    }
    for (const s of patientSessions) {
      const w = weekStartIso(s.created_at);
      (weekMap[w] ??= { consultations: 0, registrations: 0, reports: 0 }).registrations++;
    }
    for (const r of reports) {
      const w = weekStartIso(r.created_at);
      (weekMap[w] ??= { consultations: 0, registrations: 0, reports: 0 }).reports++;
    }
    const weeklyTrend = Object.entries(weekMap)
      .sort((a, b) => a[0].localeCompare(b[0]))
      .slice(-16) // last ~4 months of weeks
      .map(([week, v]) => ({ week, ...v }));

    // ── 4. Prompt construction ───────────────────────────────
    const regionCount = Object.keys(regionStats).filter((r) => r !== "Unknown").length;

    const topBy = <V extends Record<string, number>>(m: V, n: number) =>
      Object.entries(m).sort((a, b) => b[1] - a[1]).slice(0, n);

    const regionSummaries = Object.entries(regionStats)
      .sort((a, b) => b[1].consultations + b[1].registrations - (a[1].consultations + a[1].registrations))
      .slice(0, 15)
      .map(([region, s]) => {
        const avgRating = s.ratings.length
          ? (s.ratings.reduce((x, y) => x + y, 0) / s.ratings.length).toFixed(2)
          : "N/A";
        return [
          `REGION: ${region}`,
          `- Registered patients: ${s.registrations}`,
          `- Consultations: ${s.consultations}`,
          `- Reports submitted: ${s.reportsCount}`,
          `- Prescriptions: ${s.prescriptionCount}`,
          `- Districts active: ${topBy(s.byDistrict, 5).map(([k, v]) => `${k} (${v})`).join(", ") || "N/A"}`,
          `- Gender: ${Object.entries(s.byGender).map(([k, v]) => `${k}: ${v}`).join(", ") || "N/A"}`,
          `- Age groups: ${Object.entries(s.byAgeGroup).map(([k, v]) => `${k}: ${v}`).join(", ") || "N/A"}`,
          `- Service types: ${Object.entries(s.byServiceType).map(([k, v]) => `${k}: ${v}`).join(", ") || "N/A"}`,
          `- Top diagnoses: ${topBy(s.diagnoses, 5).map(([k, v]) => `${k} (${v})`).join(", ") || "None recorded"}`,
          `- Severity: ${Object.entries(s.severityCounts).map(([k, v]) => `${k}: ${v}`).join(", ") || "N/A"}`,
          `- Top medications: ${topBy(s.medications, 5).map(([k, v]) => `${k} (${v})`).join(", ") || "None"}`,
          `- Avg rating: ${avgRating} (${s.ratings.length} reviews, ${s.flaggedRatings} flagged)`,
          `- Offer redemptions: ${s.offerRedemptions} (TZS ${s.offerSubsidyTzs.toLocaleString()} subsidized)`,
        ].join("\n");
      })
      .join("\n\n");

    const globalDiagnoses: Record<string, number> = {};
    for (const d of diagnoses) if (d.description) globalDiagnoses[d.description] = (globalDiagnoses[d.description] || 0) + 1;
    const topGlobalDiagnoses =
      topBy(globalDiagnoses, 10).map(([k, v]) => `${k} (${v})`).join(", ") || "None yet";

    const activeOfferScopes = offers
      .filter((o) => o.is_active)
      .map((o) => o.district ?? o.region ?? "Tanzania-wide")
      .slice(0, 20);

    const trendSummary = weeklyTrend
      .map(
        (w) =>
          `  ${w.week}: registrations=${w.registrations}, consultations=${w.consultations}, reports=${w.reports}`,
      )
      .join("\n") || "  (no weekly activity recorded)";

    const overallAvgRating = ratings.length
      ? (ratings.reduce((s, r) => s + r.rating, 0) / ratings.length).toFixed(2)
      : "N/A";

    const prompt = `
You are a senior public-health epidemiologist preparing a situational-awareness brief for eSIRI Plus, a Tanzanian telemedicine platform. Produce a deep, data-grounded analysis — no fluff, no fabrication.

PLATFORM OVERVIEW
- Registered patients: ${patientSessions.length}
- Consultations: ${consultations.length}
- Diagnoses: ${diagnoses.length}
- Prescriptions: ${prescriptions.length}
- Clinical reports submitted: ${reports.length}
- Patient ratings: ${ratings.length} (avg ${overallAvgRating})
- Regions with activity: ${regionCount}
- Active location offers covering: ${activeOfferScopes.join(", ") || "none"}
- Offer redemptions to date: ${redemptions.length}

PLATFORM-WIDE TOP DIAGNOSES
${topGlobalDiagnoses}

WEEKLY ACTIVITY (last 16 weeks, ISO week-start)
${trendSummary}

REGIONAL BREAKDOWN (up to 15 most-active regions)
${regionSummaries || "(no regional data yet — platform is in early coverage-building phase)"}

INSTRUCTIONS
- Ground every claim in the numbers above. Flag low-n (<5) findings as tentative.
- If consultations are sparse but registrations exist, frame the report around COVERAGE and ACCESS rather than clinical patterns.
- Use epidemiological framing (incidence, prevalence, disparity, access) where appropriate.
- Consider Tanzania context: tropical disease burden, urban/rural disparities, regional health-infrastructure gaps, the Dar es Salaam metro vs. secondary regions.
- Do NOT invent numbers or regions that aren't in the data.
- Recommendations must be actionable for a platform operator (resource allocation, outreach, clinical supply, partnerships).
`.trim();

    // ── 5. Structured Output schema ──────────────────────────
    // Using json_schema with strict:true guarantees the shape at the API
    // level — we never have to reject/repair malformed output.
    const reportSchema = {
      type: "object",
      additionalProperties: false,
      required: [
        "executive_summary",
        "coverage_analysis",
        "regional_hotspots",
        "district_breakdown",
        "disease_patterns",
        "demographic_insights",
        "service_utilization",
        "satisfaction_signals",
        "offer_uptake",
        "trend_analysis",
        "equity_notes",
        "recommendations",
        "data_quality_notes",
      ],
      properties: {
        executive_summary: { type: "string" },
        coverage_analysis: { type: "string" },
        regional_hotspots: {
          type: "array",
          items: {
            type: "object",
            additionalProperties: false,
            required: ["region", "concern", "priority"],
            properties: {
              region: { type: "string" },
              concern: { type: "string" },
              priority: { type: "string", enum: ["high", "medium", "low"] },
            },
          },
        },
        district_breakdown: {
          type: "array",
          items: {
            type: "object",
            additionalProperties: false,
            required: ["region", "district", "observation"],
            properties: {
              region: { type: "string" },
              district: { type: "string" },
              observation: { type: "string" },
            },
          },
        },
        disease_patterns: { type: "string" },
        demographic_insights: { type: "string" },
        service_utilization: { type: "string" },
        satisfaction_signals: { type: "string" },
        offer_uptake: { type: "string" },
        trend_analysis: { type: "string" },
        equity_notes: { type: "string" },
        recommendations: {
          type: "array",
          items: { type: "string" },
        },
        data_quality_notes: { type: "string" },
      },
    } as const;

    // ── 6. Call OpenAI with Structured Outputs ───────────────
    const aiRes = await fetch("https://api.openai.com/v1/chat/completions", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${OPENAI_API_KEY}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        model: MODEL,
        max_tokens: MAX_TOKENS,
        temperature: 0.25,
        response_format: {
          type: "json_schema",
          json_schema: {
            name: "health_analytics_report",
            strict: true,
            schema: reportSchema,
          },
        },
        messages: [
          {
            role: "system",
            content:
              "You are a senior public-health epidemiologist. Produce rigorous, data-grounded situational awareness. Never fabricate numbers or regions. Be explicit about uncertainty.",
          },
          { role: "user", content: prompt },
        ],
      }),
    });

    if (!aiRes.ok) {
      const errText = await aiRes.text();
      console.error(`[health-analytics] OpenAI error (${aiRes.status}): ${errText}`);
      throw new Error("AI health analytics generation failed");
    }

    const aiData = await aiRes.json();
    let report: Record<string, unknown>;
    try {
      report = JSON.parse(aiData.choices[0].message.content);
    } catch {
      throw new Error("Failed to parse AI health analytics output");
    }

    // ── 7. Log ───────────────────────────────────────────────
    await logEvent({
      function_name: "generate-health-analytics",
      level: "info",
      user_id: authUserId,
      action: "health_analytics_generated",
      metadata: {
        total_consultations: consultations.length,
        total_diagnoses: diagnoses.length,
        total_prescriptions: prescriptions.length,
        total_patients: patientSessions.length,
        regions_count: regionCount,
      },
      ip_address: getClientIp(req),
    });

    // ── 8. Return ────────────────────────────────────────────
    return successResponse(
      {
        generated_at: new Date().toISOString(),
        data_summary: {
          total_patients: patientSessions.length,
          total_consultations: consultations.length,
          total_diagnoses: diagnoses.length,
          total_prescriptions: prescriptions.length,
          total_reports: reports.length,
          total_ratings: ratings.length,
          total_redemptions: redemptions.length,
          regions_count: regionCount,
        },
        report,
      },
      200,
      origin,
    );
  } catch (err) {
    return errorResponse(err, origin);
  }
});
