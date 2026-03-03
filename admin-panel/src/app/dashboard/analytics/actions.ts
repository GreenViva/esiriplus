"use server";

import { createClient } from "@/lib/supabase/server";

export interface HealthAnalyticsReport {
  generated_at: string;
  data_summary: {
    total_consultations: number;
    total_diagnoses: number;
    total_prescriptions: number;
    regions_count: number;
  };
  report: {
    executive_summary: string;
    regional_hotspots: Array<{
      region: string;
      concern: string;
      priority: "high" | "medium" | "low";
    }>;
    disease_patterns: string;
    demographic_insights: string;
    service_utilization: string;
    recommendations: string[];
    data_quality_notes: string;
  };
}

export async function generateHealthAnalytics(): Promise<{
  data?: HealthAnalyticsReport;
  error?: string;
}> {
  const serverClient = await createClient();
  const {
    data: { user },
  } = await serverClient.auth.getUser();
  if (!user) return { error: "Not authenticated" };

  try {
    const res = await fetch(
      `${process.env.NEXT_PUBLIC_SUPABASE_URL}/functions/v1/generate-health-analytics`,
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${process.env.NEXT_PUBLIC_SUPABASE_ANON_KEY}`,
          "X-Service-Key": process.env.SUPABASE_SERVICE_ROLE_KEY!,
        },
        body: JSON.stringify({}),
      },
    );

    if (!res.ok) {
      const body = await res.json().catch(() => ({}));
      return {
        error: body.error ?? `Failed to generate report (${res.status})`,
      };
    }

    const data = await res.json();
    return { data };
  } catch (e) {
    console.error("Health analytics generation failed:", e);
    return { error: "Failed to connect to analytics service" };
  }
}
