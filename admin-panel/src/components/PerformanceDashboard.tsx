"use client";

import { useEffect, useMemo, useState } from "react";
import {
  LineChart,
  Line,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  Legend,
  PieChart,
  Pie,
  Cell,
} from "recharts";

/* ------------------------------------------------------------------ */
/*  Types                                                              */
/* ------------------------------------------------------------------ */

interface PerformanceStat {
  bucket: string;
  metric_type: string;
  endpoint: string;
  avg_latency_ms: number;
  p95_latency_ms: number;
  request_count: number;
  error_count: number;
}

interface Props {
  stats: PerformanceStat[];
}

type TimeRange = "1h" | "6h" | "24h" | "7d";

const RANGE_HOURS: Record<TimeRange, number> = {
  "1h": 1,
  "6h": 6,
  "24h": 24,
  "7d": 168,
};

const COLORS = {
  teal: "#2A9D8F",
  tealLight: "#3AB8A8",
  orange: "#EA580C",
  red: "#DC2626",
  blue: "#3B82F6",
  purple: "#8B5CF6",
  amber: "#F59E0B",
  green: "#16A34A",
};

const PIE_COLORS = [COLORS.teal, COLORS.blue, COLORS.purple, COLORS.orange, COLORS.amber, COLORS.red, COLORS.green];

/* ------------------------------------------------------------------ */
/*  Component                                                          */
/* ------------------------------------------------------------------ */

export default function PerformanceDashboard({ stats }: Props) {
  const [range, setRange] = useState<TimeRange>("24h");
  const [autoRefresh, setAutoRefresh] = useState(true);

  // Auto-refresh every 30 seconds
  useEffect(() => {
    if (!autoRefresh) return;
    const interval = setInterval(() => {
      window.location.reload();
    }, 30_000);
    return () => clearInterval(interval);
  }, [autoRefresh]);

  // Filter stats by selected time range
  const filtered = useMemo(() => {
    const cutoff = new Date(Date.now() - RANGE_HOURS[range] * 3600_000);
    return stats.filter((s) => new Date(s.bucket) >= cutoff);
  }, [stats, range]);

  // ---- Overview stats ----
  const overview = useMemo(() => {
    const totalRequests = filtered.reduce((s, r) => s + r.request_count, 0);
    const totalErrors = filtered.reduce((s, r) => s + r.error_count, 0);
    const weightedLatency = filtered.reduce(
      (s, r) => s + r.avg_latency_ms * r.request_count,
      0,
    );
    const avgLatency =
      totalRequests > 0 ? Math.round(weightedLatency / totalRequests) : 0;
    const errorRate =
      totalRequests > 0
        ? ((totalErrors / totalRequests) * 100).toFixed(2)
        : "0.00";
    const uptimePct =
      totalRequests > 0
        ? (((totalRequests - totalErrors) / totalRequests) * 100).toFixed(2)
        : "100.00";
    const hours = RANGE_HOURS[range];
    const rpm =
      hours > 0 ? (totalRequests / (hours * 60)).toFixed(1) : "0.0";

    return { totalRequests, avgLatency, errorRate, uptimePct, totalErrors, rpm };
  }, [filtered, range]);

  // ---- Hourly time series ----
  const hourlyData = useMemo(() => {
    const map = new Map<
      string,
      { hour: string; avgLatency: number; p95Latency: number; requests: number; errors: number; _weight: number }
    >();
    for (const row of filtered) {
      const hour = new Date(row.bucket).toISOString().slice(0, 13) + ":00";
      const existing = map.get(hour);
      if (existing) {
        existing._weight += row.avg_latency_ms * row.request_count;
        existing.p95Latency = Math.max(existing.p95Latency, row.p95_latency_ms);
        existing.requests += row.request_count;
        existing.errors += row.error_count;
        existing.avgLatency = Math.round(existing._weight / existing.requests);
      } else {
        map.set(hour, {
          hour,
          avgLatency: Math.round(row.avg_latency_ms),
          p95Latency: Math.round(row.p95_latency_ms),
          requests: row.request_count,
          errors: row.error_count,
          _weight: row.avg_latency_ms * row.request_count,
        });
      }
    }
    return Array.from(map.values()).sort((a, b) => a.hour.localeCompare(b.hour));
  }, [filtered]);

  // ---- Endpoint breakdown ----
  const endpointData = useMemo(() => {
    const map = new Map<
      string,
      { endpoint: string; avgLatency: number; p95Latency: number; requests: number; errors: number; _weight: number }
    >();
    for (const row of filtered) {
      const existing = map.get(row.endpoint);
      if (existing) {
        existing._weight += row.avg_latency_ms * row.request_count;
        existing.p95Latency = Math.max(existing.p95Latency, row.p95_latency_ms);
        existing.requests += row.request_count;
        existing.errors += row.error_count;
        existing.avgLatency = Math.round(existing._weight / existing.requests);
      } else {
        map.set(row.endpoint, {
          endpoint: row.endpoint,
          avgLatency: Math.round(row.avg_latency_ms),
          p95Latency: Math.round(row.p95_latency_ms),
          requests: row.request_count,
          errors: row.error_count,
          _weight: row.avg_latency_ms * row.request_count,
        });
      }
    }
    return Array.from(map.values())
      .sort((a, b) => b.requests - a.requests)
      .slice(0, 15);
  }, [filtered]);

  // ---- Metric type distribution (pie) ----
  const typeDistribution = useMemo(() => {
    const map = new Map<string, number>();
    for (const row of filtered) {
      map.set(row.metric_type, (map.get(row.metric_type) || 0) + row.request_count);
    }
    return Array.from(map.entries()).map(([name, value]) => ({
      name: name === "edge_function" ? "Edge Functions" : name === "api_response" ? "API Calls" : name,
      value,
    }));
  }, [filtered]);

  const formatHour = (h: unknown) => {
    const d = new Date(String(h));
    if (range === "1h" || range === "6h") return d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
    if (range === "24h") return d.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
    return d.toLocaleDateString([], { month: "short", day: "numeric" }) + " " + d.toLocaleTimeString([], { hour: "2-digit" });
  };

  const noData = filtered.length === 0;

  return (
    <div className="space-y-6">
      {/* Controls row */}
      <div className="flex items-center justify-between">
        <div className="flex gap-1 bg-gray-100 rounded-lg p-1">
          {(["1h", "6h", "24h", "7d"] as TimeRange[]).map((r) => (
            <button
              key={r}
              onClick={() => setRange(r)}
              className={`px-3 py-1.5 text-xs font-medium rounded-md transition-colors ${
                range === r
                  ? "bg-white text-gray-900 shadow-sm"
                  : "text-gray-500 hover:text-gray-700"
              }`}
            >
              {r}
            </button>
          ))}
        </div>
        <div className="flex items-center gap-3">
          <label className="flex items-center gap-1.5 text-xs text-gray-500 cursor-pointer">
            <input
              type="checkbox"
              checked={autoRefresh}
              onChange={(e) => setAutoRefresh(e.target.checked)}
              className="rounded border-gray-300 text-brand-teal focus:ring-brand-teal"
            />
            Auto-refresh
          </label>
          <button
            onClick={() => window.location.reload()}
            className="text-xs text-gray-500 hover:text-gray-700 px-2 py-1 border border-gray-200 rounded-md"
          >
            Refresh now
          </button>
        </div>
      </div>

      {/* Overview stat cards */}
      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
        <StatCard label="Total Requests" value={overview.totalRequests.toLocaleString()} color="blue" />
        <StatCard label="Avg Latency" value={`${overview.avgLatency}ms`} color="teal" />
        <StatCard label="P95 (max)" value={`${hourlyData.length > 0 ? Math.max(...hourlyData.map((h) => h.p95Latency)) : 0}ms`} color="purple" />
        <StatCard label="Error Rate" value={`${overview.errorRate}%`} color={Number(overview.errorRate) > 5 ? "red" : "green"} />
        <StatCard label="Uptime" value={`${overview.uptimePct}%`} color={Number(overview.uptimePct) < 99 ? "orange" : "green"} />
        <StatCard label="Req/min" value={overview.rpm} color="blue" />
      </div>

      {noData && (
        <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-12 text-center">
          <div className="w-12 h-12 rounded-full bg-gray-50 flex items-center justify-center mx-auto mb-4">
            <svg className="h-6 w-6 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M3 13.125C3 12.504 3.504 12 4.125 12h2.25c.621 0 1.125.504 1.125 1.125v6.75C7.5 20.496 6.996 21 6.375 21h-2.25A1.125 1.125 0 013 19.875v-6.75zM9.75 8.625c0-.621.504-1.125 1.125-1.125h2.25c.621 0 1.125.504 1.125 1.125v11.25c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V8.625zM16.5 4.125c0-.621.504-1.125 1.125-1.125h2.25C20.496 3 21 3.504 21 4.125v15.75c0 .621-.504 1.125-1.125 1.125h-2.25a1.125 1.125 0 01-1.125-1.125V4.125z" />
            </svg>
          </div>
          <h3 className="text-sm font-semibold text-gray-900 mb-1">No metrics data yet</h3>
          <p className="text-xs text-gray-400 max-w-sm mx-auto">
            Performance metrics will appear here once the Android app starts sending data.
            Metrics are collected automatically from API calls and edge function invocations.
          </p>
        </div>
      )}

      {!noData && (
        <>
          {/* Response time trend */}
          <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-5">
            <h3 className="text-sm font-semibold text-gray-900 mb-4">Response Time Trend</h3>
            <ResponsiveContainer width="100%" height={300}>
              <LineChart data={hourlyData}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                <XAxis dataKey="hour" tickFormatter={formatHour} tick={{ fontSize: 11 }} />
                <YAxis tick={{ fontSize: 11 }} unit="ms" />
                <Tooltip
                  labelFormatter={formatHour}
                  formatter={(value: unknown, name: unknown) => [`${value}ms`, name === "avgLatency" ? "Avg Latency" : "P95 Latency"]}
                />
                <Legend formatter={(v) => (v === "avgLatency" ? "Avg Latency" : "P95 Latency")} />
                <Line type="monotone" dataKey="avgLatency" stroke={COLORS.teal} strokeWidth={2} dot={false} />
                <Line type="monotone" dataKey="p95Latency" stroke={COLORS.orange} strokeWidth={2} dot={false} strokeDasharray="5 5" />
              </LineChart>
            </ResponsiveContainer>
          </div>

          {/* Two-column: Requests over time + Type distribution */}
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <div className="lg:col-span-2 bg-white rounded-xl border border-gray-100 shadow-sm p-5">
              <h3 className="text-sm font-semibold text-gray-900 mb-4">Requests &amp; Errors Over Time</h3>
              <ResponsiveContainer width="100%" height={250}>
                <BarChart data={hourlyData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                  <XAxis dataKey="hour" tickFormatter={formatHour} tick={{ fontSize: 11 }} />
                  <YAxis tick={{ fontSize: 11 }} />
                  <Tooltip labelFormatter={formatHour} />
                  <Legend />
                  <Bar dataKey="requests" name="Requests" fill={COLORS.teal} radius={[2, 2, 0, 0]} />
                  <Bar dataKey="errors" name="Errors" fill={COLORS.red} radius={[2, 2, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            </div>

            <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-5">
              <h3 className="text-sm font-semibold text-gray-900 mb-4">Traffic by Type</h3>
              {typeDistribution.length > 0 ? (
                <ResponsiveContainer width="100%" height={250}>
                  <PieChart>
                    <Pie
                      data={typeDistribution}
                      cx="50%"
                      cy="50%"
                      innerRadius={50}
                      outerRadius={80}
                      paddingAngle={4}
                      dataKey="value"
                      label={({ name, percent }: { name?: string; percent?: number }) => `${name ?? ""} ${((percent ?? 0) * 100).toFixed(0)}%`}
                    >
                      {typeDistribution.map((_, i) => (
                        <Cell key={i} fill={PIE_COLORS[i % PIE_COLORS.length]} />
                      ))}
                    </Pie>
                    <Tooltip formatter={(value: unknown) => Number(value).toLocaleString()} />
                  </PieChart>
                </ResponsiveContainer>
              ) : (
                <div className="h-[250px] flex items-center justify-center text-xs text-gray-400">No data</div>
              )}
            </div>
          </div>

          {/* Endpoint breakdown table */}
          <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-5">
            <h3 className="text-sm font-semibold text-gray-900 mb-4">Endpoint Breakdown</h3>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b border-gray-100">
                    <th className="text-left py-2 px-3 text-xs font-medium text-gray-500">Endpoint</th>
                    <th className="text-right py-2 px-3 text-xs font-medium text-gray-500">Requests</th>
                    <th className="text-right py-2 px-3 text-xs font-medium text-gray-500">Avg Latency</th>
                    <th className="text-right py-2 px-3 text-xs font-medium text-gray-500">P95 Latency</th>
                    <th className="text-right py-2 px-3 text-xs font-medium text-gray-500">Errors</th>
                    <th className="text-right py-2 px-3 text-xs font-medium text-gray-500">Error Rate</th>
                  </tr>
                </thead>
                <tbody>
                  {endpointData.map((ep) => {
                    const errRate = ep.requests > 0 ? ((ep.errors / ep.requests) * 100).toFixed(1) : "0.0";
                    return (
                      <tr key={ep.endpoint} className="border-b border-gray-50 hover:bg-gray-50/50">
                        <td className="py-2.5 px-3 font-mono text-xs text-gray-900">{ep.endpoint}</td>
                        <td className="py-2.5 px-3 text-right text-gray-700">{ep.requests.toLocaleString()}</td>
                        <td className="py-2.5 px-3 text-right text-gray-700">{ep.avgLatency}ms</td>
                        <td className="py-2.5 px-3 text-right text-gray-700">{ep.p95Latency}ms</td>
                        <td className="py-2.5 px-3 text-right text-gray-700">{ep.errors}</td>
                        <td className="py-2.5 px-3 text-right">
                          <span
                            className={`inline-block px-2 py-0.5 rounded-full text-xs font-medium ${
                              Number(errRate) === 0
                                ? "bg-green-50 text-green-700"
                                : Number(errRate) < 5
                                ? "bg-yellow-50 text-yellow-700"
                                : "bg-red-50 text-red-700"
                            }`}
                          >
                            {errRate}%
                          </span>
                        </td>
                      </tr>
                    );
                  })}
                  {endpointData.length === 0 && (
                    <tr>
                      <td colSpan={6} className="py-8 text-center text-xs text-gray-400">
                        No endpoint data available
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </>
      )}
    </div>
  );
}

/* ------------------------------------------------------------------ */
/*  Inline stat card                                                    */
/* ------------------------------------------------------------------ */

function StatCard({ label, value, color }: { label: string; value: string | number; color: string }) {
  const bgMap: Record<string, string> = {
    blue: "bg-blue-50",
    teal: "bg-teal-50",
    purple: "bg-purple-50",
    red: "bg-red-50",
    orange: "bg-orange-50",
    green: "bg-green-50",
  };
  const textMap: Record<string, string> = {
    blue: "text-blue-600",
    teal: "text-teal-600",
    purple: "text-purple-600",
    red: "text-red-600",
    orange: "text-orange-600",
    green: "text-green-600",
  };

  return (
    <div className="bg-white rounded-xl border border-gray-100 shadow-sm p-4">
      <p className="text-xs text-gray-500 mb-1">{label}</p>
      <p className={`text-xl font-bold ${textMap[color] || "text-gray-900"}`}>{value}</p>
      <div className={`mt-2 h-1 rounded-full ${bgMap[color] || "bg-gray-100"}`} />
    </div>
  );
}
