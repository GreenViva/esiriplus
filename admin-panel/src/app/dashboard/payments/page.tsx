"use client";

import { useState, useEffect, useCallback } from "react";
import { createClient } from "@/lib/supabase/client";
import PaymentsView, { type PaymentRow, type DoctorRevenueRow } from "./PaymentsView";
import RealtimeRefresh from "@/components/RealtimeRefresh";

const PAGE_SIZE = 50;

export default function PaymentsPage() {
  const [rows, setRows] = useState<PaymentRow[]>([]);
  const [doctorRevenue, setDoctorRevenue] = useState<DoctorRevenueRow[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchData = useCallback(() => {
    const supabase = createClient();

    Promise.all([
      supabase
        .from("service_access_payments")
        .select("payment_id, base_payment_id, patient_session_id, service_type, amount, status, created_at")
        .order("created_at", { ascending: false })
        .limit(PAGE_SIZE),
      supabase
        .from("doctor_earnings")
        .select("earning_id, doctor_id, consultation_id, amount, status, created_at, consultations(consultation_id, service_type, patient_session_id, consultation_fee, patient_sessions(patient_id))")
        .order("created_at", { ascending: false })
        .limit(PAGE_SIZE),
      supabase
        .from("payments")
        .select("payment_id, patient_session_id, amount, currency, status, payment_method, payment_type, phone_number, transaction_id, service_type, created_at")
        .order("created_at", { ascending: false })
        .limit(PAGE_SIZE),
      supabase
        .from("doctor_profiles")
        .select("doctor_id, full_name, specialty, specialist_field, profile_photo_url")
        .eq("is_verified", true),
    ]).then(([servicePaymentsRes, earningsRes, paymentsRes, doctorProfilesRes]) => {
      // Build unified payment rows
      const paymentRows: PaymentRow[] = [];
      const seenIds = new Set<string>();

      // 1. Service access payments
      for (const sap of servicePaymentsRes.data ?? []) {
        seenIds.add(sap.payment_id);
        paymentRows.push({
          id: sap.payment_id,
          patient_id: sap.patient_session_id ? maskPatientId(sap.patient_session_id) : "\u2014",
          service_type: sap.service_type ?? "service_access",
          phone: "\u2014",
          amount: sap.amount ?? 0,
          currency: "TZS",
          status: sap.status ?? "completed",
          receipt: sap.base_payment_id ?? "\u2014",
          created_at: sap.created_at,
        });
      }

      // 2. Main payments table
      for (const p of paymentsRes.data ?? []) {
        if (seenIds.has(p.payment_id)) continue;
        seenIds.add(p.payment_id);
        paymentRows.push({
          id: p.payment_id,
          patient_id: p.patient_session_id ? maskPatientId(p.patient_session_id) : "\u2014",
          service_type: p.service_type ?? p.payment_type ?? "payment",
          phone: p.phone_number ?? (p.payment_method ?? "\u2014").replace(/_/g, "-"),
          amount: p.amount ?? 0,
          currency: p.currency ?? "TZS",
          status: p.status ?? "pending",
          receipt: p.transaction_id ?? "\u2014",
          created_at: p.created_at,
        });
      }

      // 3. Doctor earnings
      if (paymentRows.length === 0) {
        for (const e of earningsRes.data ?? []) {
          if (seenIds.has(e.earning_id)) continue;
          seenIds.add(e.earning_id);

          const consultation = e.consultations as unknown as {
            consultation_id: string;
            service_type: string;
            patient_session_id: string;
            consultation_fee: number;
            patient_sessions: { patient_id: string } | null;
          } | null;

          const patientId = consultation?.patient_sessions?.patient_id ?? "\u2014";
          const fullAmount = (e.amount ?? 0) * 2;

          paymentRows.push({
            id: e.earning_id,
            patient_id: patientId !== "\u2014" ? maskPatientId(patientId) : "\u2014",
            service_type: consultation?.service_type ?? "consultation",
            phone: "\u2014",
            amount: fullAmount,
            currency: "TZS",
            status: "completed",
            receipt: e.consultation_id ?? "\u2014",
            created_at: e.created_at,
          });
        }
      }

      // Sort by date descending
      paymentRows.sort((a, b) => new Date(b.created_at).getTime() - new Date(a.created_at).getTime());
      setRows(paymentRows);

      // Build doctor revenue rows
      const doctorMap = new Map<string, { earnings: number; consultations: number; lastActive: string }>();
      for (const e of earningsRes.data ?? []) {
        const existing = doctorMap.get(e.doctor_id);
        if (existing) {
          existing.earnings += e.amount ?? 0;
          existing.consultations += 1;
          if (e.created_at > existing.lastActive) existing.lastActive = e.created_at;
        } else {
          doctorMap.set(e.doctor_id, {
            earnings: e.amount ?? 0,
            consultations: 1,
            lastActive: e.created_at,
          });
        }
      }

      const profileLookup = new Map<string, { full_name: string; specialty: string; specialist_field: string | null; profile_photo_url: string | null }>();
      for (const dp of doctorProfilesRes.data ?? []) {
        profileLookup.set(dp.doctor_id, dp);
      }

      const revenue: DoctorRevenueRow[] = Array.from(doctorMap.entries()).map(([doctorId, data]) => {
        const profile = profileLookup.get(doctorId);
        return {
          doctor_id: doctorId,
          doctor_name: profile?.full_name ?? "Unknown Doctor",
          specialty: profile?.specialist_field ?? profile?.specialty ?? "General",
          profile_photo_url: profile?.profile_photo_url ?? null,
          total_revenue: data.earnings * 2,
          doctor_commission: data.earnings,
          consultation_count: data.consultations,
          last_active: data.lastActive,
        };
      });

      revenue.sort((a, b) => b.total_revenue - a.total_revenue);
      setDoctorRevenue(revenue);
      setLoading(false);
    });
  }, []);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  if (loading) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-brand-teal border-t-transparent" />
      </div>
    );
  }

  return (
    <div>
      <RealtimeRefresh
        tables={["payments", "service_access_payments", "doctor_earnings"]}
        channelName="admin-payments-realtime"
        onUpdate={fetchData}
      />
      <PaymentsView payments={rows} doctorRevenue={doctorRevenue} onRefresh={fetchData} />
    </div>
  );
}

/** Mask a patient ID for display: show first part + **** */
function maskPatientId(id: string): string {
  if (!id) return "\u2014";
  if (id.startsWith("ESP-") || id.startsWith("ESIRI-")) {
    const parts = id.split("-");
    if (parts.length >= 3) {
      return `${parts[0]}-${parts[1]}-****`;
    }
    return id.length > 8 ? id.slice(0, 8) + "****" : id;
  }
  return id.slice(0, 8) + "****";
}
