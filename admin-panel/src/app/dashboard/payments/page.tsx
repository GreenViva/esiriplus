"use client";

import { useState, useEffect, useCallback } from "react";
import { createClient } from "@/lib/supabase/client";
import PaymentsView, { type PaymentRow, type DoctorRevenueRow } from "./PaymentsView";
import RealtimeRefresh from "@/components/RealtimeRefresh";

const PAGE_SIZE = 50;

export default function PaymentsPage() {
  const [rows, setRows] = useState<PaymentRow[]>([]);
  const [doctorRevenue, setDoctorRevenue] = useState<DoctorRevenueRow[]>([]);
  // Total Revenue = sum of consultation_fee across non-failed consultations
  // with fee > 0. Mirrors the admin dashboard definition exactly.
  const [totalRevenue, setTotalRevenue] = useState(0);
  // Consultation-status counts drive the Completed / Pending / Failed cards.
  // Using consultation status (not payment-record status) so the counts match
  // the rest of the dashboard — free/test consultations aren't penalized.
  const [consultationCounts, setConsultationCounts] = useState({
    completed: 0,
    pending: 0,
    failed: 0,
  });
  const [loading, setLoading] = useState(true);

  const fetchData = useCallback(() => {
    const supabase = createClient();

    Promise.all([
      supabase
        .from("service_access_payments")
        .select("payment_id, base_payment_id, patient_session_id, service_type, amount, status, created_at")
        .order("created_at", { ascending: false })
        .limit(PAGE_SIZE),
      // Earnings: source of truth for what the app owes each doctor.
      // The fn_auto_create_doctor_earning trigger already applies the
      // platform cut (Royal 50%, Economy 30% + 20% follow-up escrow,
      // substitute 60/40, etc.). We pull all rows so we can both sum
      // pending balances and show per-doctor lifetime totals if needed.
      supabase
        .from("doctor_earnings")
        .select("doctor_id, amount, status")
        .limit(10000),
      supabase
        .from("payments")
        .select("payment_id, patient_session_id, consultation_id, amount, currency, status, payment_method, payment_type, phone_number, transaction_id, service_type, created_at, consultations(consultation_fee)")
        .order("created_at", { ascending: false })
        .limit(PAGE_SIZE),
      supabase
        .from("doctor_profiles")
        .select("doctor_id, full_name, specialty, specialist_field, profile_photo_url, phone")
        .eq("is_verified", true),
      // Consultations: drives the Completed/Pending/Failed stat cards,
      // Total Revenue, the Paid-by-Patient tab, and the consultation
      // count / last_active columns in the To-Doctor tab. The money
      // column (amount_owed) comes from doctor_earnings, not from here.
      supabase
        .from("consultations")
        .select("consultation_id, doctor_id, patient_session_id, consultation_fee, service_type, status, created_at")
        .limit(5000),
    ]).then(([servicePaymentsRes, earningsRes, paymentsRes, doctorProfilesRes, consultationsRes]) => {
      // Build unified payment rows
      const paymentRows: PaymentRow[] = [];
      const seenIds = new Set<string>();

      // Index payments by consultation_id so synthetic consultation rows
      // can be skipped where a real payment row already covers them.
      const paymentsByConsultId = new Map<string, true>();

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
        if (p.consultation_id) paymentsByConsultId.set(p.consultation_id, true);
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

      // 3. Synthetic rows from consultations — one per fee-bearing, non-failed
      // consultation that doesn't already have an explicit payment record.
      // Ensures every patient who has consulted appears in "Paid by Patient"
      // even if the M-Pesa / free-offer flow didn't write a payments row.
      const failedSet = new Set(["cancelled", "canceled", "timeout", "expired", "rejected", "failed"]);
      const consultations = (consultationsRes.data ?? []) as Array<{
        consultation_id: string;
        doctor_id: string | null;
        patient_session_id: string | null;
        consultation_fee: number | null;
        service_type: string | null;
        status: string | null;
        created_at: string;
      }>;
      for (const c of consultations) {
        const s = (c.status ?? "").toLowerCase();
        if (failedSet.has(s)) continue;
        if ((c.consultation_fee ?? 0) <= 0) continue;
        if (paymentsByConsultId.has(c.consultation_id)) continue; // already covered
        const syntheticStatus = s === "completed" ? "completed" : "pending";
        paymentRows.push({
          id: `consult-${c.consultation_id}`,
          patient_id: c.patient_session_id ? maskPatientId(c.patient_session_id) : "\u2014",
          service_type: c.service_type ?? "consultation",
          phone: "\u2014",
          amount: c.consultation_fee ?? 0,
          currency: "TZS",
          status: syntheticStatus,
          receipt: c.consultation_id,
          created_at: c.created_at,
        });
      }

      // Sort by date descending
      paymentRows.sort((a, b) => new Date(b.created_at).getTime() - new Date(a.created_at).getTime());
      setRows(paymentRows);

      // Amount owed per doctor = sum of doctor_earnings rows where the
      // status is not yet "paid". The earnings trigger has already applied
      // the platform cut, so these rows represent the doctor's share only,
      // not the gross fee the patient paid. Consultations still drive the
      // consultation_count and last_active fields since those are activity
      // metrics, not monetary.
      const doctorMap = new Map<string, { owed: number; count: number; lastActive: string }>();
      for (const c of consultations) {
        if (!c.doctor_id) continue;
        const s = (c.status ?? "").toLowerCase();
        if (failedSet.has(s)) continue;
        if ((c.consultation_fee ?? 0) <= 0) continue;
        const entry = doctorMap.get(c.doctor_id) ?? { owed: 0, count: 0, lastActive: c.created_at };
        entry.count += 1;
        if (c.created_at > entry.lastActive) entry.lastActive = c.created_at;
        doctorMap.set(c.doctor_id, entry);
      }
      // Sum unpaid earnings per doctor. These are already post-split.
      for (const e of earningsRes.data ?? []) {
        if (!e.doctor_id) continue;
        if ((e.status ?? "").toLowerCase() === "paid") continue;
        const entry = doctorMap.get(e.doctor_id) ?? { owed: 0, count: 0, lastActive: "" };
        entry.owed += e.amount ?? 0;
        doctorMap.set(e.doctor_id, entry);
      }

      const profileLookup = new Map<
        string,
        {
          full_name: string;
          specialty: string;
          specialist_field: string | null;
          profile_photo_url: string | null;
          phone: string | null;
        }
      >();
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
          phone: profile?.phone ?? null,
          amount_owed: data.owed,
          consultation_count: data.count,
          last_active: data.lastActive,
        };
      });

      revenue.sort((a, b) => b.amount_owed - a.amount_owed);
      setDoctorRevenue(revenue);

      // Completed = finished session.
      // Failed   = any terminal-not-delivered state.
      // Pending  = EVERYTHING else (paid-not-started, accepted-awaiting-slot,
      //            in_progress, scheduled, etc.) so unknown statuses still
      //            surface to the admin instead of silently disappearing.
      const completedStatuses = new Set(["completed"]);
      const countFailedStatuses = new Set([
        "cancelled", "canceled",
        "timeout", "expired", "rejected", "failed",
      ]);

      let completed = 0;
      let pending = 0;
      let failed = 0;
      let revenueTotal = 0;
      for (const c of consultations) {
        const s = (c.status ?? "").toLowerCase();
        const isFailed = countFailedStatuses.has(s);
        if (completedStatuses.has(s)) completed++;
        else if (isFailed) failed++;
        else pending++;
        // Revenue: any non-failed consultation with a fee > 0.
        if (!isFailed && (c.consultation_fee ?? 0) > 0) {
          revenueTotal += c.consultation_fee ?? 0;
        }
      }
      setConsultationCounts({ completed, pending, failed });
      setTotalRevenue(revenueTotal);

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
        tables={["payments", "service_access_payments", "doctor_earnings", "consultations"]}
        channelName="admin-payments-realtime"
        onUpdate={fetchData}
      />
      <PaymentsView
        payments={rows}
        doctorRevenue={doctorRevenue}
        totalRevenue={totalRevenue}
        consultationCounts={consultationCounts}
        onRefresh={fetchData}
      />
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
