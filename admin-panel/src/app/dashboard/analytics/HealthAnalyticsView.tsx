"use client";

import { useState, useMemo } from "react";
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  LineChart,
  Line,
  PieChart,
  Pie,
  Cell,
  Legend,
} from "recharts";
import type { ConsultationRow, DiagnosisRow, ReportRow, PatientSessionRow } from "./page";
import { generateHealthAnalytics, type HealthAnalyticsReport } from "./actions";

/* ── Helpers ─────────────────────────────────────────── */

const SERVICE_LABELS: Record<string, string> = {
  nurse: "Nurse",
  clinical_officer: "Clinical Officer",
  pharmacist: "Pharmacist",
  gp: "General Practitioner",
  general_practitioner: "General Practitioner",
  specialist: "Specialist",
  psychologist: "Psychologist",
};

function serviceLabel(slug: string): string {
  return (
    SERVICE_LABELS[slug] ??
    slug.replace(/_/g, " ").replace(/\b\w/g, (c) => c.toUpperCase())
  );
}

const PIE_COLORS = [
  "#2A9D8F",
  "#E76F51",
  "#264653",
  "#E9C46A",
  "#F4A261",
  "#6A994E",
  "#BC6C25",
  "#457B9D",
  "#9B2226",
  "#AE2012",
];

type Tab = "region" | "disease" | "trends" | "insights";

/* ── Component ───────────────────────────────────────── */

interface Props {
  initialConsultations: ConsultationRow[];
  initialDiagnoses: DiagnosisRow[];
  initialReports: ReportRow[];
  /** Every registered patient session (not date-scoped). Drives the Region
   *  and Trends tabs so they populate before any consultation is booked. */
  initialPatientSessions: PatientSessionRow[];
}

export default function HealthAnalyticsView({
  initialConsultations,
  initialDiagnoses,
  initialReports,
  initialPatientSessions,
}: Props) {
  const [activeTab, setActiveTab] = useState<Tab>("region");
  const [showRegionsModal, setShowRegionsModal] = useState(false);
  const [showDiseasesModal, setShowDiseasesModal] = useState(false);
  const consultations = initialConsultations;
  const diagnoses = initialDiagnoses;
  const reports = initialReports;
  const patientSessions = initialPatientSessions;

  // Derive the clean list of canonical regions (for the stat + modal) and a
  // per-region patient count (for the Region tab chart) in one pass.
  const { patientRegions, patientRegionCounts } = useMemo(() => {
    const counts: Record<string, number> = {};
    for (const s of patientSessions) {
      const raw = s.region?.trim();
      if (!raw) continue;
      if (raw.toUpperCase() === "TANZANIA") continue;
      // Legacy comma-joined blobs from the old Geocoder path get excluded
      // until LocationResolver backfills them with canonical names.
      if (raw.includes(",")) continue;
      counts[raw] = (counts[raw] || 0) + 1;
    }
    return {
      patientRegions: Object.keys(counts),
      patientRegionCounts: counts,
    };
  }, [patientSessions]);

  /* ── Derived stats ─────────────────────────────────── */

  // Region tab chart: patients per region (from patient_sessions), so the
  // chart populates the moment a patient registers — no need to wait for
  // consultations.
  const regionData = useMemo(
    () =>
      Object.entries(patientRegionCounts)
        .sort((a, b) => b[1] - a[1])
        .slice(0, 10)
        .map(([name, count]) => ({ name, count })),
    [patientRegionCounts]
  );

  // Regions Covered reflects where we have registered patients, not just
  // those who booked in the selected period — "Dar es Salaam" counts even
  // with zero consultations yet.
  const regionsCount = patientRegions.length;

  const serviceMap = useMemo(() => {
    const map: Record<string, number> = {};
    for (const c of consultations) {
      const svc = serviceLabel(c.service_type);
      map[svc] = (map[svc] || 0) + 1;
    }
    return map;
  }, [consultations]);

  const serviceData = useMemo(
    () =>
      Object.entries(serviceMap)
        .sort((a, b) => b[1] - a[1])
        .map(([name, value]) => ({ name, value })),
    [serviceMap]
  );

  // Distinct disease categories + their counts and a representative label.
  // Prefers ICD codes from diagnoses; falls back to service_type when no
  // diagnosis rows exist yet. Sorted by frequency desc for the modal list.
  const diseaseCategoryList = useMemo(() => {
    const fromDiagnoses = new Map<
      string,
      { count: number; descriptions: Set<string> }
    >();
    for (const d of diagnoses) {
      if (!d.icd_code) continue;
      const key = d.icd_code;
      const entry = fromDiagnoses.get(key) ?? { count: 0, descriptions: new Set() };
      entry.count += 1;
      if (d.description) entry.descriptions.add(d.description);
      fromDiagnoses.set(key, entry);
    }

    if (fromDiagnoses.size > 0) {
      return Array.from(fromDiagnoses.entries())
        .map(([code, { count, descriptions }]) => ({
          code,
          label: Array.from(descriptions)[0] ?? code,
          count,
        }))
        .sort((a, b) => b.count - a.count);
    }

    const fromServices = new Map<string, number>();
    for (const c of consultations) {
      fromServices.set(c.service_type, (fromServices.get(c.service_type) ?? 0) + 1);
    }
    return Array.from(fromServices.entries())
      .map(([code, count]) => ({ code, label: serviceLabel(code), count }))
      .sort((a, b) => b.count - a.count);
  }, [consultations, diagnoses]);

  const diseaseCategories = diseaseCategoryList.length;

  /* ── Trends: consultations + registrations per week ── */

  const trendData = useMemo(() => {
    const weekStartOf = (iso: string): string => {
      const d = new Date(iso);
      const day = d.getUTCDay();
      const diff = d.getUTCDate() - day + (day === 0 ? -6 : 1);
      const weekStart = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), diff));
      return weekStart.toISOString().slice(0, 10);
    };

    const consults: Record<string, number> = {};
    for (const c of consultations) {
      const key = weekStartOf(c.created_at);
      consults[key] = (consults[key] || 0) + 1;
    }
    const regs: Record<string, number> = {};
    for (const s of patientSessions) {
      const key = weekStartOf(s.created_at);
      regs[key] = (regs[key] || 0) + 1;
    }

    const allWeeks = Array.from(
      new Set([...Object.keys(consults), ...Object.keys(regs)]),
    ).sort();

    return allWeeks.map((week) => ({
      week: new Date(week + "T00:00:00Z").toLocaleDateString("en-US", {
        month: "short",
        day: "numeric",
        timeZone: "UTC",
      }),
      consultations: consults[week] ?? 0,
      registrations: regs[week] ?? 0,
    }));
  }, [consultations, patientSessions]);

  /* ── Severity breakdown ─────────────────────────── */

  const severityData = useMemo(() => {
    const map: Record<string, number> = {};
    for (const d of diagnoses) {
      const sev = d.severity || "unknown";
      map[sev] = (map[sev] || 0) + 1;
    }
    return Object.entries(map)
      .sort((a, b) => b[1] - a[1])
      .map(([name, value]) => ({ name: name.charAt(0).toUpperCase() + name.slice(1), value }));
  }, [diagnoses]);

  /* ── Render ──────────────────────────────────────── */

  return (
    <div>
      {/* Header */}
      <div className="flex items-start justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Health Analytics</h1>
          <p className="text-sm text-gray-400 mt-0.5">
            Regional health patterns and disease mapping
          </p>
        </div>
        <a
          href="/dashboard/analytics"
          className="flex items-center gap-2 px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-200 rounded-xl hover:bg-gray-50 transition-colors"
        >
          <svg
            className="h-4 w-4"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            strokeWidth={1.5}
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0l3.181 3.183a8.25 8.25 0 0013.803-3.7M4.031 9.865a8.25 8.25 0 0113.803-3.7l3.181 3.182"
            />
          </svg>
          Refresh Data
        </a>
      </div>

      {/* Stat cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4 mb-6">
        <AnalyticsStatCard
          label="Total Reports"
          value={reports.length}
          icon={
            <svg className="h-5 w-5 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m2.25 0H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c.621 0 1.125-.504 1.125-1.125V11.25a9 9 0 00-9-9z" />
            </svg>
          }
        />
        <AnalyticsStatCard
          label="Regions Covered"
          value={regionsCount}
          onClick={regionsCount > 0 ? () => setShowRegionsModal(true) : undefined}
          icon={
            <svg className="h-5 w-5 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M15 10.5a3 3 0 11-6 0 3 3 0 016 0z" />
              <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 10.5c0 7.142-7.5 11.25-7.5 11.25S4.5 17.642 4.5 10.5a7.5 7.5 0 1115 0z" />
            </svg>
          }
        />
        <AnalyticsStatCard
          label="Disease Categories"
          value={diseaseCategories}
          onClick={diseaseCategories > 0 ? () => setShowDiseasesModal(true) : undefined}
          icon={
            <svg className="h-5 w-5 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M3 13.125C3 12.504 3.504 12 4.125 12h2.25c.621 0 1.125.504 1.125 1.125v6.75C7.5 20.496 6.996 21 6.375 21h-2.25A1.125 1.125 0 013 19.875v-6.75zM9.75 8.625c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125v11.25c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V8.625zM16.5 4.125c0-.621.504-1.125 1.125-1.125h2.25C20.496 3 21 3.504 21 4.125v15.75c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V4.125z" />
            </svg>
          }
        />
      </div>

      {/* Tabs */}
      <div className="flex gap-1 mb-6">
        <TabButton
          active={activeTab === "region"}
          onClick={() => setActiveTab("region")}
          icon={
            <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M15 10.5a3 3 0 11-6 0 3 3 0 016 0z" />
              <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 10.5c0 7.142-7.5 11.25-7.5 11.25S4.5 17.642 4.5 10.5a7.5 7.5 0 1115 0z" />
            </svg>
          }
          label="By Region"
        />
        <TabButton
          active={activeTab === "disease"}
          onClick={() => setActiveTab("disease")}
          icon={
            <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M3 13.125C3 12.504 3.504 12 4.125 12h2.25c.621 0 1.125.504 1.125 1.125v6.75C7.5 20.496 6.996 21 6.375 21h-2.25A1.125 1.125 0 013 19.875v-6.75zM9.75 8.625c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125v11.25c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V8.625zM16.5 4.125c0-.621.504-1.125 1.125-1.125h2.25C20.496 3 21 3.504 21 4.125v15.75c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V4.125z" />
            </svg>
          }
          label="By Disease"
        />
        <TabButton
          active={activeTab === "trends"}
          onClick={() => setActiveTab("trends")}
          icon={
            <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M2.25 18L9 11.25l4.306 4.307a11.95 11.95 0 015.814-5.519l2.74-1.22m0 0l-5.94-2.28m5.94 2.28l-2.28 5.941" />
            </svg>
          }
          label="Trends"
        />
        <TabButton
          active={activeTab === "insights"}
          onClick={() => setActiveTab("insights")}
          icon={
            <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 21a9.004 9.004 0 008.716-6.747M12 21a9.004 9.004 0 01-8.716-6.747M12 21c2.485 0 4.5-4.03 4.5-9S14.485 3 12 3m0 18c-2.485 0-4.5-4.03-4.5-9S9.515 3 12 3m0 0a8.997 8.997 0 017.843 4.582M12 3a8.997 8.997 0 00-7.843 4.582m15.686 0A11.953 11.953 0 0112 10.5c-2.998 0-5.74-1.1-7.843-2.918m15.686 0A8.959 8.959 0 0121 12c0 .778-.099 1.533-.284 2.253m0 0A17.919 17.919 0 0112 16.5c-3.162 0-6.133-.815-8.716-2.247m0 0A9.015 9.015 0 013 12c0-1.605.42-3.113 1.157-4.418" />
            </svg>
          }
          label="Data Insights"
        />
      </div>

      {/* Tab content */}
      {activeTab === "region" && <RegionTab data={regionData} />}
      {activeTab === "disease" && (
        <DiseaseTab serviceData={serviceData} severityData={severityData} diagnoses={diagnoses} />
      )}
      {activeTab === "trends" && (
        <TrendsTab
          data={trendData}
          totalConsultations={consultations.length}
          totalRegistrations={patientSessions.length}
        />
      )}
      {activeTab === "insights" && <InsightsTab />}

      {showRegionsModal && (
        <RegionsCoveredModal
          regions={patientRegions}
          onClose={() => setShowRegionsModal(false)}
        />
      )}

      {showDiseasesModal && (
        <DiseaseCategoriesModal
          items={diseaseCategoryList}
          onClose={() => setShowDiseasesModal(false)}
        />
      )}
    </div>
  );
}

/* ── Regions Covered modal ───────────────────────────── */

function RegionsCoveredModal({
  regions,
  onClose,
}: {
  regions: string[];
  onClose: () => void;
}) {
  const sorted = [...regions].sort((a, b) => a.localeCompare(b));
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      onClick={onClose}
    >
      <div
        className="bg-white rounded-2xl shadow-2xl max-w-md w-full max-h-[80vh] overflow-hidden flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100">
          <div>
            <h2 className="text-lg font-bold text-gray-900">Regions Covered</h2>
            <p className="text-xs text-gray-400 mt-0.5">
              {sorted.length} location{sorted.length !== 1 ? "s" : ""} with registered patients
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="text-gray-400 hover:text-gray-600 p-1 rounded-lg hover:bg-gray-100"
          >
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
        <ul className="overflow-y-auto divide-y divide-gray-50">
          {sorted.map((name) => (
            <li key={name} className="px-5 py-3 text-sm text-gray-800 flex items-center gap-2">
              <svg className="h-4 w-4 text-brand-teal flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M15 10.5a3 3 0 11-6 0 3 3 0 016 0z" />
                <path strokeLinecap="round" strokeLinejoin="round" d="M19.5 10.5c0 7.142-7.5 11.25-7.5 11.25S4.5 17.642 4.5 10.5a7.5 7.5 0 1115 0z" />
              </svg>
              {name}
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}

/* ── Disease Categories modal ───────────────────────── */

function DiseaseCategoriesModal({
  items,
  onClose,
}: {
  items: { code: string; label: string; count: number }[];
  onClose: () => void;
}) {
  const total = items.reduce((s, i) => s + i.count, 0);
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"
      onClick={onClose}
    >
      <div
        className="bg-white rounded-2xl shadow-2xl max-w-lg w-full max-h-[80vh] overflow-hidden flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between px-5 py-4 border-b border-gray-100">
          <div>
            <h2 className="text-lg font-bold text-gray-900">Disease Categories</h2>
            <p className="text-xs text-gray-400 mt-0.5">
              {items.length} categor{items.length === 1 ? "y" : "ies"} — {total} diagnos{total === 1 ? "is" : "es"}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="text-gray-400 hover:text-gray-600 p-1 rounded-lg hover:bg-gray-100"
          >
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
        <ul className="overflow-y-auto divide-y divide-gray-50">
          {items.map((item) => {
            const pct = total > 0 ? Math.round((item.count / total) * 100) : 0;
            return (
              <li key={item.code} className="px-5 py-3 flex items-center gap-3">
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-medium text-gray-900 truncate">{item.label}</p>
                  {item.code !== item.label && (
                    <p className="text-xs text-gray-400 font-mono">{item.code}</p>
                  )}
                </div>
                <div className="flex items-center gap-3 flex-shrink-0">
                  <span className="text-sm font-semibold text-gray-800">{item.count}</span>
                  <span className="text-xs text-gray-400 w-10 text-right">{pct}%</span>
                </div>
              </li>
            );
          })}
        </ul>
      </div>
    </div>
  );
}

/* ── Stat Card (matches screenshot design) ──────────── */

function AnalyticsStatCard({
  label,
  value,
  icon,
  onClick,
}: {
  label: string;
  value: number;
  icon: React.ReactNode;
  onClick?: () => void;
}) {
  const body = (
    <>
      <div className="flex items-start justify-between mb-3">
        <p className="text-sm font-medium text-gray-500">{label}</p>
        {icon}
      </div>
      <p className="text-3xl font-bold text-gray-900">{value}</p>
      {onClick && (
        <p className="text-xs text-brand-teal mt-2 font-medium">View list →</p>
      )}
    </>
  );
  if (onClick) {
    return (
      <button
        type="button"
        onClick={onClick}
        className="bg-white rounded-xl border border-gray-100 shadow-sm p-5 w-full text-left cursor-pointer hover:border-brand-teal hover:shadow-md transition-all"
      >
        {body}
      </button>
    );
  }
  return (
    <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-5">
      {body}
    </div>
  );
}

/* ── Tab Button ─────────────────────────────────────── */

function TabButton({
  active,
  onClick,
  icon,
  label,
}: {
  active: boolean;
  onClick: () => void;
  icon: React.ReactNode;
  label: string;
}) {
  return (
    <button
      onClick={onClick}
      className={`flex items-center gap-1.5 px-4 py-2 rounded-full text-sm font-medium transition-colors ${
        active
          ? "bg-white text-gray-900 shadow-sm border border-gray-200"
          : "text-gray-500 hover:text-gray-700 hover:bg-gray-100"
      }`}
    >
      {icon}
      {label}
    </button>
  );
}

/* ── By Region Tab ──────────────────────────────────── */

function RegionTab({ data }: { data: { name: string; count: number }[] }) {
  if (data.length === 0) {
    return (
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-8">
        <h2 className="text-lg font-bold text-gray-900">Patients by Region</h2>
        <p className="text-sm text-gray-400 mt-1">
          Top 10 regions with most registered patients
        </p>
        <div className="flex items-center justify-center py-16">
          <p className="text-gray-400">No registered patients yet</p>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-6">
        <h2 className="text-lg font-bold text-gray-900">Patients by Region</h2>
        <p className="text-sm text-gray-400 mt-1 mb-6">
          Top 10 regions with most registered patients
        </p>
        <div className="h-80">
          <ResponsiveContainer width="100%" height="100%" minHeight={200}>
            <BarChart data={data} layout="vertical" margin={{ left: 20, right: 20 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#f3f4f6" />
              <XAxis type="number" tick={{ fontSize: 12, fill: "#9ca3af" }} />
              <YAxis
                dataKey="name"
                type="category"
                tick={{ fontSize: 12, fill: "#374151" }}
                width={120}
              />
              <Tooltip
                contentStyle={{
                  borderRadius: "12px",
                  border: "1px solid #e5e7eb",
                  boxShadow: "0 4px 6px -1px rgb(0 0 0 / 0.1)",
                }}
              />
              <Bar dataKey="count" fill="#2A9D8F" radius={[0, 6, 6, 0]} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </div>

      {/* Region table */}
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b border-gray-100 bg-gray-50/50">
              <th className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                Region
              </th>
              <th className="text-right px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                Patients
              </th>
              <th className="text-right px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                Share
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-50">
            {(() => {
              const total = data.reduce((s, x) => s + x.count, 0);
              return data.map((r) => {
              const pct = total > 0 ? Math.round((r.count / total) * 100) : 0;
              return (
                <tr key={r.name} className="hover:bg-gray-50/50 transition-colors">
                  <td className="px-5 py-3.5 text-gray-900 font-medium">{r.name}</td>
                  <td className="px-5 py-3.5 text-gray-700 text-right">{r.count}</td>
                  <td className="px-5 py-3.5 text-gray-500 text-right">{pct}%</td>
                </tr>
              );
            });
            })()}
          </tbody>
        </table>
      </div>
    </div>
  );
}

/* ── By Disease Tab ─────────────────────────────────── */

function DiseaseTab({
  serviceData,
  severityData,
  diagnoses,
}: {
  serviceData: { name: string; value: number }[];
  severityData: { name: string; value: number }[];
  diagnoses: DiagnosisRow[];
}) {
  if (serviceData.length === 0) {
    return (
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-8">
        <h2 className="text-lg font-bold text-gray-900">Consultations by Service Type</h2>
        <p className="text-sm text-gray-400 mt-1">
          Distribution of consultations across service categories
        </p>
        <div className="flex items-center justify-center py-16">
          <p className="text-gray-400">No consultation data available yet</p>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      {/* Service type pie chart */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-6">
          <h2 className="text-lg font-bold text-gray-900">By Service Type</h2>
          <p className="text-sm text-gray-400 mt-1 mb-4">
            Distribution across service categories
          </p>
          <div className="h-72">
            <ResponsiveContainer width="100%" height="100%" minHeight={200}>
              <PieChart>
                <Pie
                  data={serviceData}
                  cx="50%"
                  cy="50%"
                  innerRadius={60}
                  outerRadius={100}
                  paddingAngle={3}
                  dataKey="value"
                >
                  {serviceData.map((_, i) => (
                    <Cell key={i} fill={PIE_COLORS[i % PIE_COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip
                  contentStyle={{
                    borderRadius: "12px",
                    border: "1px solid #e5e7eb",
                    boxShadow: "0 4px 6px -1px rgb(0 0 0 / 0.1)",
                  }}
                />
                <Legend
                  verticalAlign="bottom"
                  height={36}
                  formatter={(value: string) => (
                    <span className="text-xs text-gray-600">{value}</span>
                  )}
                />
              </PieChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Severity breakdown */}
        <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-6">
          <h2 className="text-lg font-bold text-gray-900">Diagnosis Severity</h2>
          <p className="text-sm text-gray-400 mt-1 mb-4">
            Breakdown by severity level
          </p>
          {severityData.length > 0 ? (
            <div className="h-72">
              <ResponsiveContainer width="100%" height="100%" minHeight={200}>
                <BarChart data={severityData} margin={{ left: 10, right: 10 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#f3f4f6" />
                  <XAxis dataKey="name" tick={{ fontSize: 12, fill: "#9ca3af" }} />
                  <YAxis tick={{ fontSize: 12, fill: "#9ca3af" }} />
                  <Tooltip
                    contentStyle={{
                      borderRadius: "12px",
                      border: "1px solid #e5e7eb",
                      boxShadow: "0 4px 6px -1px rgb(0 0 0 / 0.1)",
                    }}
                  />
                  <Bar dataKey="value" fill="#E76F51" radius={[6, 6, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          ) : (
            <div className="flex items-center justify-center py-16">
              <p className="text-gray-400">No diagnosis data yet</p>
            </div>
          )}
        </div>
      </div>

      {/* Recent diagnoses table */}
      {diagnoses.length > 0 && (
        <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
          <div className="px-5 py-4 border-b border-gray-100">
            <h2 className="text-lg font-bold text-gray-900">Recent Diagnoses</h2>
            <p className="text-sm text-gray-400 mt-0.5">Latest {Math.min(diagnoses.length, 20)} diagnoses</p>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b border-gray-100 bg-gray-50/50">
                  <th className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                    Description
                  </th>
                  <th className="text-left px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                    ICD Code
                  </th>
                  <th className="text-center px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                    Severity
                  </th>
                  <th className="text-right px-5 py-3 text-xs font-semibold text-gray-500 uppercase tracking-wider">
                    Date
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {diagnoses.slice(0, 20).map((d) => (
                  <tr key={d.id} className="hover:bg-gray-50/50 transition-colors">
                    <td className="px-5 py-3.5 text-gray-900">{d.description}</td>
                    <td className="px-5 py-3.5 text-gray-500 font-mono text-xs">
                      {d.icd_code || "—"}
                    </td>
                    <td className="px-5 py-3.5 text-center">
                      <SeverityBadge severity={d.severity} />
                    </td>
                    <td className="px-5 py-3.5 text-gray-500 text-right whitespace-nowrap">
                      {d.created_at
                        ? new Date(d.created_at).toLocaleDateString("en-US", {
                            month: "short",
                            day: "numeric",
                            year: "numeric",
                          })
                        : "—"}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
}

function SeverityBadge({ severity }: { severity: string }) {
  const map: Record<string, { bg: string; text: string }> = {
    mild: { bg: "bg-green-50", text: "text-green-700" },
    moderate: { bg: "bg-amber-50", text: "text-amber-700" },
    severe: { bg: "bg-red-50", text: "text-red-700" },
    critical: { bg: "bg-red-100", text: "text-red-800" },
  };
  const s = map[severity.toLowerCase()] ?? { bg: "bg-gray-100", text: "text-gray-600" };
  return (
    <span
      className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${s.bg} ${s.text}`}
    >
      {severity.charAt(0).toUpperCase() + severity.slice(1)}
    </span>
  );
}

/* ── Trends Tab ─────────────────────────────────────── */

function TrendsTab({
  data,
  totalConsultations,
  totalRegistrations,
}: {
  data: { week: string; consultations: number; registrations: number }[];
  totalConsultations: number;
  totalRegistrations: number;
}) {
  if (data.length === 0) {
    return (
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-8">
        <h2 className="text-lg font-bold text-gray-900">Weekly Activity</h2>
        <p className="text-sm text-gray-400 mt-1">
          Patient registrations and consultation volume over time
        </p>
        <div className="flex items-center justify-center py-16">
          <p className="text-gray-400">No trend data available yet</p>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-6">
        <div className="flex items-start justify-between mb-6">
          <div>
            <h2 className="text-lg font-bold text-gray-900">Weekly Activity</h2>
            <p className="text-sm text-gray-400 mt-1">
              Patient registrations and consultation volume over time
            </p>
          </div>
          <div className="flex items-start gap-6">
            <div className="text-right">
              <p className="text-2xl font-bold text-gray-900">{totalRegistrations}</p>
              <p className="text-xs text-gray-400">Total registrations</p>
            </div>
            <div className="text-right">
              <p className="text-2xl font-bold text-gray-900">{totalConsultations}</p>
              <p className="text-xs text-gray-400">Total consultations</p>
            </div>
          </div>
        </div>
        <div className="h-80">
          <ResponsiveContainer width="100%" height="100%" minHeight={200}>
            <LineChart data={data} margin={{ left: 10, right: 10 }}>
              <CartesianGrid strokeDasharray="3 3" stroke="#f3f4f6" />
              <XAxis dataKey="week" tick={{ fontSize: 11, fill: "#9ca3af" }} />
              <YAxis tick={{ fontSize: 12, fill: "#9ca3af" }} />
              <Tooltip
                contentStyle={{
                  borderRadius: "12px",
                  border: "1px solid #e5e7eb",
                  boxShadow: "0 4px 6px -1px rgb(0 0 0 / 0.1)",
                }}
              />
              <Legend wrapperStyle={{ fontSize: 12 }} />
              <Line
                type="monotone"
                dataKey="registrations"
                name="Patient registrations"
                stroke="#3B82F6"
                strokeWidth={2.5}
                dot={{ r: 4, fill: "#3B82F6" }}
                activeDot={{ r: 6 }}
              />
              <Line
                type="monotone"
                dataKey="consultations"
                name="Consultations"
                stroke="#2A9D8F"
                strokeWidth={2.5}
                dot={{ r: 4, fill: "#2A9D8F" }}
                activeDot={{ r: 6 }}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </div>
    </div>
  );
}

/* ── AI Insights Tab ────────────────────────────────── */

function InsightsTab() {
  const [report, setReport] = useState<HealthAnalyticsReport | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleGenerate = async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await generateHealthAnalytics();
      if (result.error) {
        setError(result.error);
      } else if (result.data) {
        setReport(result.data);
      }
    } catch {
      setError("An unexpected error occurred");
    } finally {
      setLoading(false);
    }
  };

  // Initial state — show generate button
  if (!report && !loading && !error) {
    return (
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-8">
        <div className="flex items-center gap-3 mb-6">
          <div className="w-10 h-10 rounded-xl bg-teal-50 flex items-center justify-center">
            <svg className="h-5 w-5 text-teal-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 18v-5.25m0 0a6.01 6.01 0 001.5-.189m-1.5.189a6.01 6.01 0 01-1.5-.189m3.75 7.478a12.06 12.06 0 01-4.5 0m3.75 2.383a14.406 14.406 0 01-3 0M14.25 18v-.192c0-.983.658-1.823 1.508-2.316a7.5 7.5 0 10-7.517 0c.85.493 1.509 1.333 1.509 2.316V18" />
            </svg>
          </div>
          <div>
            <h2 className="text-lg font-bold text-gray-900">AI Health Insights</h2>
            <p className="text-sm text-gray-400">
              Generate AI-powered health analytics from patient session data
            </p>
          </div>
        </div>
        <div className="flex flex-col items-center py-12">
          <p className="text-sm text-gray-500 mb-6 text-center max-w-md">
            Analyze consultation data, diagnoses, and demographics across all regions using AI.
            This uses patient location data to identify health patterns and generate public health recommendations.
          </p>
          <button
            onClick={handleGenerate}
            className="px-6 py-3 bg-teal-600 text-white font-medium rounded-xl hover:bg-teal-700 transition-colors flex items-center gap-2"
          >
            <svg className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09zM18.259 8.715L18 9.75l-.259-1.035a3.375 3.375 0 00-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 002.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 002.455 2.456L21.75 6l-1.036.259a3.375 3.375 0 00-2.455 2.456zM16.894 20.567L16.5 21.75l-.394-1.183a2.25 2.25 0 00-1.423-1.423L13.5 18.75l1.183-.394a2.25 2.25 0 001.423-1.423l.394-1.183.394 1.183a2.25 2.25 0 001.423 1.423l1.183.394-1.183.394a2.25 2.25 0 00-1.423 1.423z" />
            </svg>
            Generate AI Health Report
          </button>
          <p className="text-xs text-gray-400 mt-3">Typically takes 10-20 seconds</p>
        </div>
      </div>
    );
  }

  // Loading state
  if (loading) {
    return (
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-8">
        <div className="flex flex-col items-center py-16">
          <div className="animate-spin h-8 w-8 border-2 border-teal-600 border-t-transparent rounded-full mb-4" />
          <p className="text-sm text-gray-700 font-medium">Analyzing health data across regions...</p>
          <p className="text-xs text-gray-400 mt-1">This may take 10-20 seconds</p>
        </div>
      </div>
    );
  }

  // Error state
  if (error && !report) {
    return (
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-8">
        <div className="flex flex-col items-center py-12">
          <div className="w-12 h-12 rounded-full bg-red-50 flex items-center justify-center mb-4">
            <svg className="h-6 w-6 text-red-500" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z" />
            </svg>
          </div>
          <p className="text-sm text-red-600 font-medium mb-2">Failed to generate report</p>
          <p className="text-xs text-gray-500 mb-4">{error}</p>
          <button
            onClick={handleGenerate}
            className="px-4 py-2 bg-gray-100 text-gray-700 text-sm font-medium rounded-xl hover:bg-gray-200 transition-colors"
          >
            Try Again
          </button>
        </div>
      </div>
    );
  }

  // Report display
  const r = report!.report;
  const summary = report!.data_summary;

  return (
    <div className="space-y-4">
      {/* Header with regenerate button */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 rounded-xl bg-teal-50 flex items-center justify-center">
            <svg className="h-5 w-5 text-teal-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M9.813 15.904L9 18.75l-.813-2.846a4.5 4.5 0 00-3.09-3.09L2.25 12l2.846-.813a4.5 4.5 0 003.09-3.09L9 5.25l.813 2.846a4.5 4.5 0 003.09 3.09L15.75 12l-2.846.813a4.5 4.5 0 00-3.09 3.09zM18.259 8.715L18 9.75l-.259-1.035a3.375 3.375 0 00-2.455-2.456L14.25 6l1.036-.259a3.375 3.375 0 002.455-2.456L18 2.25l.259 1.035a3.375 3.375 0 002.455 2.456L21.75 6l-1.036.259a3.375 3.375 0 00-2.455 2.456zM16.894 20.567L16.5 21.75l-.394-1.183a2.25 2.25 0 00-1.423-1.423L13.5 18.75l1.183-.394a2.25 2.25 0 001.423-1.423l.394-1.183.394 1.183a2.25 2.25 0 001.423 1.423l1.183.394-1.183.394a2.25 2.25 0 00-1.423 1.423z" />
            </svg>
          </div>
          <div>
            <h2 className="text-lg font-bold text-gray-900">AI Health Insights</h2>
            <p className="text-xs text-gray-400">
              Generated {new Date(report!.generated_at).toLocaleString()} — {summary.total_patients ?? 0} patients, {summary.total_consultations} consultations, {summary.total_diagnoses} diagnoses, {summary.total_reports ?? 0} reports, {summary.total_ratings ?? 0} ratings, {summary.regions_count} regions
            </p>
          </div>
        </div>
        <button
          onClick={handleGenerate}
          disabled={loading}
          className="flex items-center gap-2 px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-200 rounded-xl hover:bg-gray-50 transition-colors disabled:opacity-50"
        >
          <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0l3.181 3.183a8.25 8.25 0 0013.803-3.7M4.031 9.865a8.25 8.25 0 0113.803-3.7l3.181 3.182" />
          </svg>
          Regenerate
        </button>
      </div>

      {/* Executive Summary */}
      <ReportSection title="Executive Summary">
        <p className="text-sm text-gray-700 leading-relaxed">{r.executive_summary}</p>
      </ReportSection>

      {/* Coverage Analysis */}
      {r.coverage_analysis && (
        <ReportSection title="Coverage Analysis">
          <p className="text-sm text-gray-700 leading-relaxed">{r.coverage_analysis}</p>
        </ReportSection>
      )}

      {/* Regional Hotspots */}
      {r.regional_hotspots && r.regional_hotspots.length > 0 && (
        <ReportSection title="Regional Hotspots">
          <div className="space-y-3">
            {r.regional_hotspots.map((hotspot, i) => (
              <div key={i} className="flex items-start gap-3 p-3 rounded-lg bg-gray-50 border border-gray-100">
                <PriorityBadge priority={hotspot.priority} />
                <div>
                  <p className="text-sm font-semibold text-gray-900">{hotspot.region}</p>
                  <p className="text-sm text-gray-600 mt-0.5">{hotspot.concern}</p>
                </div>
              </div>
            ))}
          </div>
        </ReportSection>
      )}

      {/* District Breakdown */}
      {r.district_breakdown && r.district_breakdown.length > 0 && (
        <ReportSection title="District Breakdown">
          <div className="space-y-2">
            {r.district_breakdown.map((d, i) => (
              <div key={i} className="p-3 rounded-lg bg-gray-50 border border-gray-100">
                <p className="text-sm font-semibold text-gray-900">
                  {d.district}
                  <span className="text-xs text-gray-400 font-normal ml-2">{d.region}</span>
                </p>
                <p className="text-sm text-gray-600 mt-0.5">{d.observation}</p>
              </div>
            ))}
          </div>
        </ReportSection>
      )}

      {/* Disease Patterns */}
      <ReportSection title="Disease Patterns">
        <p className="text-sm text-gray-700 leading-relaxed">{r.disease_patterns}</p>
      </ReportSection>

      {/* Demographic Insights */}
      <ReportSection title="Demographic Insights">
        <p className="text-sm text-gray-700 leading-relaxed">{r.demographic_insights}</p>
      </ReportSection>

      {/* Service Utilization */}
      <ReportSection title="Service Utilization">
        <p className="text-sm text-gray-700 leading-relaxed">{r.service_utilization}</p>
      </ReportSection>

      {/* Satisfaction Signals */}
      {r.satisfaction_signals && (
        <ReportSection title="Patient Satisfaction Signals">
          <p className="text-sm text-gray-700 leading-relaxed">{r.satisfaction_signals}</p>
        </ReportSection>
      )}

      {/* Offer Uptake */}
      {r.offer_uptake && (
        <ReportSection title="Offer Uptake & Subsidy Impact">
          <p className="text-sm text-gray-700 leading-relaxed">{r.offer_uptake}</p>
        </ReportSection>
      )}

      {/* Trend Analysis */}
      {r.trend_analysis && (
        <ReportSection title="Trend Analysis">
          <p className="text-sm text-gray-700 leading-relaxed">{r.trend_analysis}</p>
        </ReportSection>
      )}

      {/* Equity Notes */}
      {r.equity_notes && (
        <ReportSection title="Equity & Access">
          <p className="text-sm text-gray-700 leading-relaxed">{r.equity_notes}</p>
        </ReportSection>
      )}

      {/* Recommendations */}
      {r.recommendations && r.recommendations.length > 0 && (
        <ReportSection title="Recommendations">
          <div className="space-y-2">
            {r.recommendations.map((rec, i) => (
              <div key={i} className="flex gap-3 p-3 rounded-lg bg-gray-50 border border-gray-100">
                <div className="flex-shrink-0 w-6 h-6 rounded-full bg-teal-50 flex items-center justify-center mt-0.5">
                  <span className="text-xs font-bold text-teal-700">{i + 1}</span>
                </div>
                <p className="text-sm text-gray-700">{rec}</p>
              </div>
            ))}
          </div>
        </ReportSection>
      )}

      {/* Data Quality Notes */}
      <ReportSection title="Data Quality Notes">
        <p className="text-sm text-gray-500 italic">{r.data_quality_notes}</p>
      </ReportSection>
    </div>
  );
}

/* ── Report helpers ──────────────────────────────────── */

function ReportSection({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-6">
      <h3 className="text-base font-bold text-gray-900 mb-3">{title}</h3>
      {children}
    </div>
  );
}

function PriorityBadge({ priority }: { priority: "high" | "medium" | "low" }) {
  const styles = {
    high: { bg: "bg-red-50", text: "text-red-700" },
    medium: { bg: "bg-amber-50", text: "text-amber-700" },
    low: { bg: "bg-green-50", text: "text-green-700" },
  };
  const s = styles[priority] ?? styles.low;
  return (
    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium shrink-0 ${s.bg} ${s.text}`}>
      {priority.charAt(0).toUpperCase() + priority.slice(1)}
    </span>
  );
}
