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
import type { ConsultationRow, DiagnosisRow, ReportRow } from "./page";

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
}

export default function HealthAnalyticsView({
  initialConsultations,
  initialDiagnoses,
  initialReports,
}: Props) {
  const [activeTab, setActiveTab] = useState<Tab>("region");
  const consultations = initialConsultations;
  const diagnoses = initialDiagnoses;
  const reports = initialReports;

  /* ── Derived stats ─────────────────────────────────── */

  const regionMap = useMemo(() => {
    const map: Record<string, number> = {};
    for (const c of consultations) {
      const region = c.patient_sessions?.region;
      if (region) {
        map[region] = (map[region] || 0) + 1;
      }
    }
    return map;
  }, [consultations]);

  const regionData = useMemo(
    () =>
      Object.entries(regionMap)
        .sort((a, b) => b[1] - a[1])
        .slice(0, 10)
        .map(([name, count]) => ({ name, count })),
    [regionMap]
  );

  const regionsCount = Object.keys(regionMap).length;

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

  const diseaseCategories = useMemo(() => {
    const codes = new Set<string>();
    for (const d of diagnoses) {
      if (d.icd_code) codes.add(d.icd_code);
    }
    // Fall back to service types if no ICD codes exist yet
    if (codes.size === 0) {
      for (const c of consultations) {
        codes.add(c.service_type);
      }
    }
    return codes.size;
  }, [consultations, diagnoses]);

  /* ── Trends: consultations per week ──────────────── */

  const trendData = useMemo(() => {
    const weekMap: Record<string, number> = {};
    for (const c of consultations) {
      const d = new Date(c.created_at);
      // week start (Monday) using UTC to avoid timezone drift
      const day = d.getUTCDay();
      const diff = d.getUTCDate() - day + (day === 0 ? -6 : 1);
      const weekStart = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth(), diff));
      const key = weekStart.toISOString().slice(0, 10);
      weekMap[key] = (weekMap[key] || 0) + 1;
    }
    return Object.entries(weekMap)
      .sort((a, b) => a[0].localeCompare(b[0]))
      .map(([week, count]) => ({
        week: new Date(week + "T00:00:00Z").toLocaleDateString("en-US", {
          month: "short",
          day: "numeric",
          timeZone: "UTC",
        }),
        consultations: count,
      }));
  }, [consultations]);

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

  /* ── AI Insights ───────────────────────────────── */

  const insights = useMemo(() => {
    const items: string[] = [];

    // Top region
    if (regionData.length > 0) {
      items.push(
        `The region with the highest consultation volume is "${regionData[0].name}" with ${regionData[0].count} consultations.`
      );
    }

    // Top service
    if (serviceData.length > 0) {
      items.push(
        `"${serviceData[0].name}" is the most used service category, accounting for ${serviceData[0].value} out of ${consultations.length} total consultations.`
      );
    }

    // Gender split
    const genderMap: Record<string, number> = {};
    for (const c of consultations) {
      const sex = c.patient_sessions?.sex || "unknown";
      genderMap[sex] = (genderMap[sex] || 0) + 1;
    }
    const genderEntries = Object.entries(genderMap).sort((a, b) => b[1] - a[1]);
    if (genderEntries.length > 0 && genderEntries[0][0] !== "unknown") {
      const pct = Math.round((genderEntries[0][1] / consultations.length) * 100);
      items.push(
        `${pct}% of consultations are from ${genderEntries[0][0]} patients.`
      );
    }

    // Severity
    if (severityData.length > 0) {
      items.push(
        `Most common diagnosis severity is "${severityData[0].name}" (${severityData[0].value} diagnoses).`
      );
    }

    // Report coverage
    if (consultations.length > 0) {
      const pct = Math.round((reports.length / consultations.length) * 100);
      items.push(
        `${pct}% of consultations have generated patient reports (${reports.length} reports from ${consultations.length} consultations).`
      );
    }

    if (items.length === 0) {
      items.push("Not enough data to generate insights yet. As consultations and diagnoses grow, patterns will appear here.");
    }

    return items;
  }, [consultations, regionData, serviceData, severityData, reports]);

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
      {activeTab === "trends" && <TrendsTab data={trendData} total={consultations.length} />}
      {activeTab === "insights" && <InsightsTab insights={insights} />}
    </div>
  );
}

/* ── Stat Card (matches screenshot design) ──────────── */

function AnalyticsStatCard({
  label,
  value,
  icon,
}: {
  label: string;
  value: number;
  icon: React.ReactNode;
}) {
  return (
    <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-5">
      <div className="flex items-start justify-between mb-3">
        <p className="text-sm font-medium text-gray-500">{label}</p>
        {icon}
      </div>
      <p className="text-3xl font-bold text-gray-900">{value}</p>
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
        <h2 className="text-lg font-bold text-gray-900">Consultations by Region</h2>
        <p className="text-sm text-gray-400 mt-1">
          Top 10 regions with most consultations
        </p>
        <div className="flex items-center justify-center py-16">
          <p className="text-gray-400">No location data available yet</p>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-6">
        <h2 className="text-lg font-bold text-gray-900">Consultations by Region</h2>
        <p className="text-sm text-gray-400 mt-1 mb-6">
          Top 10 regions with most consultations
        </p>
        <div className="h-80">
          <ResponsiveContainer width="100%" height="100%">
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
                Consultations
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
            <ResponsiveContainer width="100%" height="100%">
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
              <ResponsiveContainer width="100%" height="100%">
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
  total,
}: {
  data: { week: string; consultations: number }[];
  total: number;
}) {
  if (data.length === 0) {
    return (
      <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-8">
        <h2 className="text-lg font-bold text-gray-900">Consultation Trends</h2>
        <p className="text-sm text-gray-400 mt-1">
          Weekly consultation volume over time
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
            <h2 className="text-lg font-bold text-gray-900">Consultation Trends</h2>
            <p className="text-sm text-gray-400 mt-1">
              Weekly consultation volume over time
            </p>
          </div>
          <div className="text-right">
            <p className="text-2xl font-bold text-gray-900">{total}</p>
            <p className="text-xs text-gray-400">Total consultations</p>
          </div>
        </div>
        <div className="h-80">
          <ResponsiveContainer width="100%" height="100%">
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
              <Line
                type="monotone"
                dataKey="consultations"
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

function InsightsTab({ insights }: { insights: string[] }) {
  return (
    <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-6">
      <div className="flex items-center gap-3 mb-6">
        <div className="w-10 h-10 rounded-xl bg-brand-teal/10 flex items-center justify-center">
          <svg
            className="h-5 w-5 text-brand-teal"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            strokeWidth={1.5}
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              d="M12 18v-5.25m0 0a6.01 6.01 0 001.5-.189m-1.5.189a6.01 6.01 0 01-1.5-.189m3.75 7.478a12.06 12.06 0 01-4.5 0m3.75 2.383a14.406 14.406 0 01-3 0M14.25 18v-.192c0-.983.658-1.823 1.508-2.316a7.5 7.5 0 10-7.517 0c.85.493 1.509 1.333 1.509 2.316V18"
            />
          </svg>
        </div>
        <div>
          <h2 className="text-lg font-bold text-gray-900">Data Insights</h2>
          <p className="text-sm text-gray-400">
            Summary observations from your health data
          </p>
        </div>
      </div>
      <div className="space-y-3">
        {insights.map((insight, i) => (
          <div
            key={i}
            className="flex gap-3 p-4 rounded-xl bg-gray-50 border border-gray-100"
          >
            <div className="flex-shrink-0 w-6 h-6 rounded-full bg-brand-teal/10 flex items-center justify-center mt-0.5">
              <span className="text-xs font-bold text-brand-teal">{i + 1}</span>
            </div>
            <p className="text-sm text-gray-700 leading-relaxed">{insight}</p>
          </div>
        ))}
      </div>
    </div>
  );
}
