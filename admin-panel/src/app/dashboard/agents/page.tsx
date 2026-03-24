"use client";

import { useState, useEffect, useCallback } from "react";
import { createClient } from "@/lib/supabase/client";
import type { AgentProfile } from "@/lib/types/database";

interface AgentEarning {
  id: string;
  consultation_id: string;
  amount: number;
  status: string;
  created_at: string;
}

interface AgentConsultation {
  consultation_id: string;
  service_type: string;
  service_tier: string;
  consultation_fee: number;
  status: string;
  created_at: string;
  patient_session_id: string;
  doctor_id: string;
}

interface AgentSummary {
  totalConsultations: number;
  totalEarnings: number;
  pendingEarnings: number;
  paidEarnings: number;
}

export default function AgentsPage() {
  const [agents, setAgents] = useState<AgentProfile[]>([]);
  const [agentSummaries, setAgentSummaries] = useState<Record<string, AgentSummary>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [search, setSearch] = useState("");
  const [selectedAgent, setSelectedAgent] = useState<AgentProfile | null>(null);
  const [consultations, setConsultations] = useState<AgentConsultation[]>([]);
  const [earnings, setEarnings] = useState<AgentEarning[]>([]);
  const [detailLoading, setDetailLoading] = useState(false);
  const [payingId, setPayingId] = useState<string | null>(null);

  const fetchAgents = useCallback(async () => {
    const supabase = createClient();
    const { data, error: err } = await supabase
      .from("agent_profiles")
      .select("*")
      .order("created_at", { ascending: false });

    if (err) {
      setError(err.message);
      setLoading(false);
      return;
    }

    const agentList = data ?? [];
    setAgents(agentList);

    // Fetch summaries for all agents
    const summaries: Record<string, AgentSummary> = {};
    for (const agent of agentList) {
      const [consultRes, earningsRes] = await Promise.all([
        supabase
          .from("consultations")
          .select("consultation_id", { count: "exact", head: true })
          .eq("agent_id", agent.agent_id),
        supabase
          .from("agent_earnings")
          .select("amount, status")
          .eq("agent_id", agent.agent_id),
      ]);

      const earningsList = earningsRes.data ?? [];
      summaries[agent.agent_id] = {
        totalConsultations: consultRes.count ?? 0,
        totalEarnings: earningsList.reduce((s, e) => s + e.amount, 0),
        pendingEarnings: earningsList.filter((e) => e.status === "pending").reduce((s, e) => s + e.amount, 0),
        paidEarnings: earningsList.filter((e) => e.status === "paid").reduce((s, e) => s + e.amount, 0),
      };
    }
    setAgentSummaries(summaries);
    setLoading(false);
  }, []);

  useEffect(() => {
    fetchAgents();
  }, [fetchAgents]);

  const openAgentDetail = async (agent: AgentProfile) => {
    setSelectedAgent(agent);
    setDetailLoading(true);

    const supabase = createClient();
    const [consultRes, earningsRes] = await Promise.all([
      supabase
        .from("consultations")
        .select("consultation_id, service_type, service_tier, consultation_fee, status, created_at, patient_session_id, doctor_id")
        .eq("agent_id", agent.agent_id)
        .order("created_at", { ascending: false }),
      supabase
        .from("agent_earnings")
        .select("*")
        .eq("agent_id", agent.agent_id)
        .order("created_at", { ascending: false }),
    ]);

    setConsultations(consultRes.data ?? []);
    setEarnings(earningsRes.data ?? []);
    setDetailLoading(false);
  };

  const markAsPaid = async (earningId: string) => {
    setPayingId(earningId);
    const supabase = createClient();
    const { error: err } = await supabase
      .from("agent_earnings")
      .update({ status: "paid" })
      .eq("id", earningId);

    if (err) {
      alert("Failed to mark as paid: " + err.message);
    } else {
      setEarnings((prev) => prev.map((e) => (e.id === earningId ? { ...e, status: "paid" } : e)));
      // Refresh summaries
      fetchAgents();
    }
    setPayingId(null);
  };

  const filtered = agents.filter((a) => {
    if (!search) return true;
    const q = search.toLowerCase();
    return (
      a.full_name.toLowerCase().includes(q) ||
      a.email.toLowerCase().includes(q) ||
      a.mobile_number.includes(q) ||
      a.place_of_residence.toLowerCase().includes(q)
    );
  });

  const formatTZS = (amount: number) =>
    new Intl.NumberFormat("en-TZ", { style: "currency", currency: "TZS", maximumFractionDigits: 0 }).format(amount);

  const formatDate = (iso: string) =>
    new Date(iso).toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" });

  // ── Agent Detail View ─────────────────────────────────────────────────────
  if (selectedAgent) {
    const summary = agentSummaries[selectedAgent.agent_id];
    const earningsMap = new Map(earnings.map((e) => [e.consultation_id, e]));

    return (
      <div>
        {/* Back button + header */}
        <div className="flex items-center gap-3 mb-6">
          <button
            onClick={() => setSelectedAgent(null)}
            className="p-2 rounded-lg hover:bg-gray-100 transition-colors"
          >
            <svg className="h-5 w-5 text-gray-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
            </svg>
          </button>
          <div>
            <h1 className="text-2xl font-bold text-gray-900">{selectedAgent.full_name}</h1>
            <p className="text-sm text-gray-500">{selectedAgent.email} &middot; {selectedAgent.mobile_number}</p>
          </div>
        </div>

        {/* Summary cards */}
        <div className="grid grid-cols-2 md:grid-cols-4 gap-4 mb-6">
          <SummaryCard label="Total Consultations" value={String(summary?.totalConsultations ?? 0)} color="blue" />
          <SummaryCard label="Total Commission" value={formatTZS(summary?.totalEarnings ?? 0)} color="amber" />
          <SummaryCard label="Pending Payment" value={formatTZS(summary?.pendingEarnings ?? 0)} color="orange" />
          <SummaryCard label="Paid" value={formatTZS(summary?.paidEarnings ?? 0)} color="green" />
        </div>

        {/* Consultations table */}
        {detailLoading ? (
          <div className="flex items-center justify-center py-20">
            <div className="h-8 w-8 animate-spin rounded-full border-4 border-brand-teal border-t-transparent" />
          </div>
        ) : consultations.length === 0 ? (
          <div className="text-center py-20 text-sm text-gray-500">No consultations found for this agent.</div>
        ) : (
          <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="bg-gray-50 border-b border-gray-200">
                    <th className="text-left px-4 py-3 font-semibold text-gray-600">Date</th>
                    <th className="text-left px-4 py-3 font-semibold text-gray-600">Patient Session</th>
                    <th className="text-left px-4 py-3 font-semibold text-gray-600">Service</th>
                    <th className="text-left px-4 py-3 font-semibold text-gray-600">Tier</th>
                    <th className="text-right px-4 py-3 font-semibold text-gray-600">Fee</th>
                    <th className="text-right px-4 py-3 font-semibold text-gray-600">Agent 10%</th>
                    <th className="text-center px-4 py-3 font-semibold text-gray-600">Status</th>
                    <th className="text-center px-4 py-3 font-semibold text-gray-600">Payment</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-100">
                  {consultations.map((c) => {
                    const earning = earningsMap.get(c.consultation_id);
                    const commission = earning?.amount ?? Math.floor(c.consultation_fee * 0.1);

                    return (
                      <tr key={c.consultation_id} className="hover:bg-gray-50/50">
                        <td className="px-4 py-3 text-gray-700">{formatDate(c.created_at)}</td>
                        <td className="px-4 py-3 text-gray-500 font-mono text-xs">
                          {c.patient_session_id.slice(0, 8)}...
                        </td>
                        <td className="px-4 py-3 text-gray-700 capitalize">{c.service_type.replace("_", " ")}</td>
                        <td className="px-4 py-3">
                          <span
                            className={`inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-medium ${
                              c.service_tier === "ROYAL"
                                ? "bg-purple-50 text-purple-700 border border-purple-200"
                                : "bg-blue-50 text-blue-700 border border-blue-200"
                            }`}
                          >
                            {c.service_tier}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-right text-gray-900 font-medium">{formatTZS(c.consultation_fee)}</td>
                        <td className="px-4 py-3 text-right text-amber-700 font-semibold">{formatTZS(commission)}</td>
                        <td className="px-4 py-3 text-center">
                          <span
                            className={`inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-medium ${
                              c.status === "completed"
                                ? "bg-green-50 text-green-700"
                                : c.status === "active"
                                ? "bg-blue-50 text-blue-700"
                                : "bg-gray-50 text-gray-600"
                            }`}
                          >
                            {c.status}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-center">
                          {earning ? (
                            earning.status === "paid" ? (
                              <span className="inline-flex items-center px-2.5 py-1 rounded-lg text-xs font-medium bg-green-50 text-green-700">
                                Paid
                              </span>
                            ) : (
                              <button
                                onClick={() => markAsPaid(earning.id)}
                                disabled={payingId === earning.id}
                                className="inline-flex items-center px-3 py-1 rounded-lg text-xs font-medium bg-amber-500 text-white hover:bg-amber-600 transition-colors disabled:opacity-50"
                              >
                                {payingId === earning.id ? "..." : "Pay"}
                              </button>
                            )
                          ) : c.status !== "completed" ? (
                            <span className="text-xs text-gray-400">Pending</span>
                          ) : (
                            <span className="text-xs text-gray-400">—</span>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </div>
    );
  }

  // ── Agent List View ───────────────────────────────────────────────────────
  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">eSIRIPlus Agents</h1>
          <p className="text-sm text-gray-500 mt-1">
            {agents.length} registered agent{agents.length !== 1 ? "s" : ""}
          </p>
        </div>
      </div>

      <div className="mb-5">
        <div className="relative max-w-md">
          <svg
            className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-gray-400"
            fill="none"
            viewBox="0 0 24 24"
            stroke="currentColor"
            strokeWidth={2}
          >
            <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-5.197-5.197m0 0A7.5 7.5 0 105.196 5.196a7.5 7.5 0 0010.607 10.607z" />
          </svg>
          <input
            type="text"
            placeholder="Search by name, email, phone, or residence..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full pl-10 pr-4 py-2.5 rounded-xl border border-gray-200 text-sm focus:outline-none focus:ring-2 focus:ring-brand-teal/30 focus:border-brand-teal"
          />
        </div>
      </div>

      {error && (
        <div className="mb-4 p-4 rounded-xl bg-red-50 border border-red-200 flex items-start justify-between">
          <div>
            <p className="text-sm font-medium text-red-700">Failed to load agents.</p>
            <p className="text-xs text-red-500 mt-1">{error}</p>
          </div>
          <button
            onClick={() => { setError(null); setLoading(true); fetchAgents(); }}
            className="ml-4 px-3 py-1.5 text-xs font-medium text-red-700 bg-red-100 rounded-lg hover:bg-red-200 transition-colors flex-shrink-0"
          >
            Retry
          </button>
        </div>
      )}

      {loading ? (
        <div className="flex items-center justify-center py-20">
          <div className="h-8 w-8 animate-spin rounded-full border-4 border-brand-teal border-t-transparent" />
        </div>
      ) : filtered.length === 0 ? (
        <div className="text-center py-20">
          <svg className="mx-auto h-12 w-12 text-gray-300" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M18 18.72a9.094 9.094 0 003.741-.479 3 3 0 00-4.682-2.72m.94 3.198l.001.031c0 .225-.012.447-.037.666A11.944 11.944 0 0112 21c-2.17 0-4.207-.576-5.963-1.584A6.062 6.062 0 016 18.719m12 0a5.971 5.971 0 00-.941-3.197m0 0A5.995 5.995 0 0012 12.75a5.995 5.995 0 00-5.058 2.772m0 0a3 3 0 00-4.681 2.72 8.986 8.986 0 003.74.477m.94-3.197a5.971 5.971 0 00-.94 3.197M15 6.75a3 3 0 11-6 0 3 3 0 016 0zm6 3a2.25 2.25 0 11-4.5 0 2.25 2.25 0 014.5 0zm-13.5 0a2.25 2.25 0 11-4.5 0 2.25 2.25 0 014.5 0z" />
          </svg>
          <p className="mt-3 text-sm text-gray-500">
            {search ? "No agents match your search." : "No agents have signed up yet."}
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-3 gap-4">
          {filtered.map((agent) => {
            const summary = agentSummaries[agent.agent_id];
            return (
              <div
                key={agent.id}
                onClick={() => openAgentDetail(agent)}
                className="bg-white rounded-xl border border-gray-200 p-5 hover:shadow-md hover:border-amber-300 transition-all cursor-pointer"
              >
                <div className="flex items-start gap-3">
                  <div className="w-10 h-10 rounded-full bg-amber-100 flex items-center justify-center flex-shrink-0">
                    <span className="text-sm font-bold text-amber-700">
                      {agent.full_name.split(" ").map((w) => w[0]).join("").slice(0, 2).toUpperCase()}
                    </span>
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-semibold text-gray-900 truncate">{agent.full_name}</p>
                    <p className="text-xs text-gray-500 truncate">{agent.email}</p>
                  </div>
                  <span
                    className={`inline-flex items-center px-2 py-0.5 rounded-full text-[11px] font-medium ${
                      agent.is_active
                        ? "bg-green-50 text-green-700 border border-green-200"
                        : "bg-red-50 text-red-700 border border-red-200"
                    }`}
                  >
                    {agent.is_active ? "Active" : "Inactive"}
                  </span>
                </div>

                <div className="mt-4 space-y-2">
                  <DetailRow label="Phone" value={agent.mobile_number} />
                  <DetailRow label="Residence" value={agent.place_of_residence} />
                  <DetailRow
                    label="Joined"
                    value={new Date(agent.created_at).toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric" })}
                  />
                </div>

                {/* Summary stats */}
                {summary && (
                  <div className="mt-3 pt-3 border-t border-gray-100 flex items-center gap-4 text-xs">
                    <div>
                      <span className="text-gray-400">Consultations: </span>
                      <span className="font-semibold text-gray-700">{summary.totalConsultations}</span>
                    </div>
                    <div>
                      <span className="text-gray-400">Earnings: </span>
                      <span className="font-semibold text-amber-700">{formatTZS(summary.totalEarnings)}</span>
                    </div>
                    {summary.pendingEarnings > 0 && (
                      <div className="ml-auto">
                        <span className="inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-medium bg-orange-50 text-orange-700 border border-orange-200">
                          {formatTZS(summary.pendingEarnings)} unpaid
                        </span>
                      </div>
                    )}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}

function SummaryCard({ label, value, color }: { label: string; value: string; color: string }) {
  const colorMap: Record<string, string> = {
    blue: "bg-blue-50 border-blue-200 text-blue-700",
    amber: "bg-amber-50 border-amber-200 text-amber-700",
    orange: "bg-orange-50 border-orange-200 text-orange-700",
    green: "bg-green-50 border-green-200 text-green-700",
  };
  return (
    <div className={`rounded-xl border p-4 ${colorMap[color] ?? colorMap.blue}`}>
      <p className="text-xs font-medium opacity-70">{label}</p>
      <p className="text-lg font-bold mt-1">{value}</p>
    </div>
  );
}

function DetailRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center text-xs">
      <span className="text-gray-400 w-20 flex-shrink-0">{label}</span>
      <span className="text-gray-700 truncate">{value}</span>
    </div>
  );
}
