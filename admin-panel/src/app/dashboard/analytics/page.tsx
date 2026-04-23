"use client";

import { useState, useEffect, useCallback } from "react";
import { createClient } from "@/lib/supabase/client";
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
  service_region: string | null;
  service_district: string | null;
  service_ward: string | null;
  created_at: string;
  patient_sessions: {
    region: string | null;
    age: string | null;
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
  created_at: string;
}

export interface PatientSessionRow {
  session_id: string;
  region: string | null;
  service_district: string | null;
  age: string | null;
  sex: string | null;
  created_at: string;
}

type DateRange = "30d" | "90d" | "1y" | "all";

function getDateCutoff(range: DateRange): string | null {
  if (range === "all") return null;
  const now = new Date();
  if (range === "30d") now.setDate(now.getDate() - 30);
  else if (range === "90d") now.setDate(now.getDate() - 90);
  else if (range === "1y") now.setFullYear(now.getFullYear() - 1);
  return now.toISOString();
}

const PAGE_SIZE = 500;

export default function HealthAnalyticsPage() {
  const [consultations, setConsultations] = useState<ConsultationRow[]>([]);
  const [diagnoses, setDiagnoses] = useState<DiagnosisRow[]>([]);
  const [reports, setReports] = useState<ReportRow[]>([]);
  // Footprint = all patient sessions with region/demographics, NOT date-scoped.
  // Feeds the Regions Covered stat AND the Region/Trends tab content so they
  // populate as soon as patients are registered, not only when they book.
  const [patientSessions, setPatientSessions] = useState<PatientSessionRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [dateRange, setDateRange] = useState<DateRange>("90d");

  const fetchData = useCallback(() => {
    setLoading(true);
    const supabase = createClient();
    const cutoff = getDateCutoff(dateRange);

    let consultQuery = supabase
      .from("consultations")
      .select(
        "consultation_id, patient_session_id, doctor_id, status, service_type, chief_complaint, consultation_type, service_region, service_district, service_ward, created_at, patient_sessions(region, age, sex)"
      )
      .order("created_at", { ascending: false })
      .limit(PAGE_SIZE);

    let diagQuery = supabase
      .from("diagnoses")
      .select("id, consultation_id, icd_code, description, severity, created_at")
      .order("created_at", { ascending: false })
      .limit(PAGE_SIZE);

    // Reports live in consultation_reports (not patient_reports — that table
    // doesn't exist server-side). Previous query silently returned zero rows
    // so the "Total Reports" stat was always 0.
    let reportQuery = supabase
      .from("consultation_reports")
      .select("report_id, consultation_id, created_at")
      .order("created_at", { ascending: false })
      .limit(PAGE_SIZE);

    if (cutoff) {
      consultQuery = consultQuery.gte("created_at", cutoff);
      diagQuery = diagQuery.gte("created_at", cutoff);
      reportQuery = reportQuery.gte("created_at", cutoff);
    }

    // Patient-session footprint (region + demographics + created_at).
    // NOT date-scoped — we want the full footprint so Region/Trends tabs
    // populate even before any consultations have been booked.
    const patientSessionsQuery = supabase
      .from("patient_sessions")
      .select("session_id, region, service_district, age, sex, created_at")
      .order("created_at", { ascending: false })
      .limit(PAGE_SIZE);

    Promise.all([
      consultQuery,
      diagQuery.then((res) => (res.error ? { data: [] } : res)),
      reportQuery,
      patientSessionsQuery,
    ]).then(([consultationsRes, diagnosesRes, reportsRes, sessionsRes]) => {
      setConsultations((consultationsRes.data ?? []) as unknown as ConsultationRow[]);
      setDiagnoses((diagnosesRes.data ?? []) as unknown as DiagnosisRow[]);
      setReports((reportsRes.data ?? []) as unknown as ReportRow[]);
      setPatientSessions((sessionsRes.data ?? []) as unknown as PatientSessionRow[]);
      setLoading(false);
    });
  }, [dateRange]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  return (
    <>
      <RealtimeRefresh tables={["consultations", "diagnoses", "consultation_reports", "patient_sessions"]} channelName="analytics-realtime" onUpdate={fetchData} />

      {/* Date range picker */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Health Analytics</h1>
          <p className="text-sm text-gray-400 mt-0.5">
            Consultation data, diagnosis patterns, and regional insights
          </p>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-sm text-gray-500">Period:</span>
          <select
            value={dateRange}
            onChange={(e) => setDateRange(e.target.value as DateRange)}
            className="px-3 py-2 bg-white border border-gray-200 rounded-xl text-sm text-gray-700 focus:outline-none focus:ring-2 focus:ring-brand-teal/30 focus:border-brand-teal"
          >
            <option value="30d">Last 30 days</option>
            <option value="90d">Last 90 days</option>
            <option value="1y">Last year</option>
            <option value="all">All time</option>
          </select>
        </div>
      </div>

      {loading ? (
        <div className="flex items-center justify-center py-20">
          <div className="h-8 w-8 animate-spin rounded-full border-4 border-brand-teal border-t-transparent" />
        </div>
      ) : (
        <HealthAnalyticsView
          initialConsultations={consultations}
          initialDiagnoses={diagnoses}
          initialReports={reports}
          initialPatientSessions={patientSessions}
        />
      )}
    </>
  );
}
