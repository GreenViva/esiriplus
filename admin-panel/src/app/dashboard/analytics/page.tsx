export const dynamic = "force-dynamic";

import { createAdminClient } from "@/lib/supabase/admin";
import HealthAnalyticsView from "./HealthAnalyticsView";
import RealtimeRefresh from "@/components/RealtimeRefresh";

export interface ConsultationRow {
  consultation_id: string;
  patient_session_id: string;
  doctor_id: string;
  status: string;
  service_type: string;
  chief_complaint: string | null;
  consultation_type: string | null;
  created_at: string;
  patient_sessions: {
    region: string | null;
    age_group: string | null;
    sex: string | null;
  } | null;
}

export interface DiagnosisRow {
  id: string;
  consultation_id: string;
  icd_code: string | null;
  description: string;
  severity: string;
  created_at: string | null;
}

export interface ReportRow {
  report_id: string;
  consultation_id: string;
  generated_at: string;
}

export default async function HealthAnalyticsPage() {
  const supabase = createAdminClient();

  const [consultationsRes, diagnosesRes, reportsRes] = await Promise.all([
    supabase
      .from("consultations")
      .select(
        "consultation_id, patient_session_id, doctor_id, status, service_type, chief_complaint, consultation_type, created_at, patient_sessions(region, age_group, sex)"
      )
      .order("created_at", { ascending: false })
      .limit(2000),
    supabase
      .from("diagnoses")
      .select("id, consultation_id, icd_code, description, severity, created_at")
      .order("created_at", { ascending: false })
      .limit(2000),
    supabase
      .from("patient_reports")
      .select("report_id, consultation_id, generated_at")
      .order("generated_at", { ascending: false })
      .limit(2000),
  ]);

  return (
    <>
    <RealtimeRefresh tables={["consultations", "diagnoses", "patient_reports"]} channelName="analytics-realtime" />
    <HealthAnalyticsView
      initialConsultations={(consultationsRes.data ?? []) as unknown as ConsultationRow[]}
      initialDiagnoses={(diagnosesRes.data ?? []) as unknown as DiagnosisRow[]}
      initialReports={(reportsRes.data ?? []) as unknown as ReportRow[]}
    />
    </>
  );
}
