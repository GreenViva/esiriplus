export const dynamic = "force-dynamic";

import { createAdminClient } from "@/lib/supabase/admin";
import PaymentsView, { type PaymentRow, type DoctorRevenueRow } from "./PaymentsView";
import RealtimeRefresh from "@/components/RealtimeRefresh";

export default async function PaymentsPage() {
  const supabase = createAdminClient();

  // Query all data sources in parallel
  const [
    servicePaymentsRes,
    earningsRes,
    paymentsRes,
    doctorProfilesRes,
  ] = await Promise.all([
    // Service access payments (M-Pesa completed purchases with patient info)
    supabase
      .from("service_access_payments")
      .select("payment_id, base_payment_id, patient_session_id, service_type, amount, status, created_at")
      .order("created_at", { ascending: false })
      .limit(500),

    // Doctor earnings joined with consultations + patient sessions
    // (this is where the real financial data lives currently)
    supabase
      .from("doctor_earnings")
      .select("earning_id, doctor_id, consultation_id, amount, status, created_at, consultations(consultation_id, service_type, patient_session_id, consultation_fee, patient_sessions(patient_id))")
      .order("created_at", { ascending: false })
      .limit(500),

    // Main payments table (M-Pesa STK push records)
    supabase
      .from("payments")
      .select("payment_id, patient_session_id, amount, currency, status, payment_method, payment_type, phone_number, transaction_id, service_type, created_at")
      .order("created_at", { ascending: false })
      .limit(500),

    // Doctor profiles for "By Doctor" tab
    supabase
      .from("doctor_profiles")
      .select("doctor_id, full_name, specialty, specialist_field, profile_photo_url")
      .eq("is_verified", true),
  ]);

  // Build unified payment rows
  const rows: PaymentRow[] = [];
  const seenIds = new Set<string>();

  // 1. Service access payments (highest priority — actual M-Pesa records)
  for (const sap of servicePaymentsRes.data ?? []) {
    seenIds.add(sap.payment_id);
    rows.push({
      id: sap.payment_id,
      patient_id: sap.patient_session_id ? maskPatientId(sap.patient_session_id) : "—",
      service_type: sap.service_type ?? "service_access",
      phone: "—",
      amount: sap.amount ?? 0,
      currency: "TZS",
      status: sap.status ?? "completed",
      receipt: sap.base_payment_id ?? "—",
      created_at: sap.created_at,
    });
  }

  // 2. Main payments table
  for (const p of paymentsRes.data ?? []) {
    if (seenIds.has(p.payment_id)) continue;
    seenIds.add(p.payment_id);
    rows.push({
      id: p.payment_id,
      patient_id: p.patient_session_id ? maskPatientId(p.patient_session_id) : "—",
      service_type: p.service_type ?? p.payment_type ?? "payment",
      phone: p.phone_number ?? (p.payment_method ?? "—").replace(/_/g, "-"),
      amount: p.amount ?? 0,
      currency: p.currency ?? "TZS",
      status: p.status ?? "pending",
      receipt: p.transaction_id ?? "—",
      created_at: p.created_at,
    });
  }

  // 3. Doctor earnings → derive full consultation payments
  //    (only if service_access_payments and payments tables are empty)
  if (rows.length === 0) {
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

      const patientId = consultation?.patient_sessions?.patient_id ?? "—";
      // Doctor earnings = 50% commission, full consultation fee = double
      const fullAmount = (e.amount ?? 0) * 2;

      rows.push({
        id: e.earning_id,
        patient_id: patientId !== "—" ? maskPatientId(patientId) : "—",
        service_type: consultation?.service_type ?? "consultation",
        phone: "—",
        amount: fullAmount,
        currency: "TZS",
        status: "completed",
        receipt: e.consultation_id ?? "—",
        created_at: e.created_at,
      });
    }
  }

  // Sort by date descending
  rows.sort((a, b) => new Date(b.created_at).getTime() - new Date(a.created_at).getTime());

  // Build doctor revenue rows — aggregate doctor_earnings by doctor_id
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

  // Build profile lookup
  const profileLookup = new Map<string, { full_name: string; specialty: string; specialist_field: string | null; profile_photo_url: string | null }>();
  for (const dp of doctorProfilesRes.data ?? []) {
    profileLookup.set(dp.doctor_id, dp);
  }

  const doctorRevenue: DoctorRevenueRow[] = Array.from(doctorMap.entries()).map(([doctorId, data]) => {
    const profile = profileLookup.get(doctorId);
    return {
      doctor_id: doctorId,
      doctor_name: profile?.full_name ?? "Unknown Doctor",
      specialty: profile?.specialist_field ?? profile?.specialty ?? "General",
      profile_photo_url: profile?.profile_photo_url ?? null,
      total_revenue: data.earnings * 2, // full consultation fee (doctor earns 50%)
      doctor_commission: data.earnings,
      consultation_count: data.consultations,
      last_active: data.lastActive,
    };
  });

  // Sort by total revenue descending
  doctorRevenue.sort((a, b) => b.total_revenue - a.total_revenue);

  return (
    <div>
      <RealtimeRefresh
        tables={["payments", "service_access_payments", "doctor_earnings"]}
        channelName="admin-payments-realtime"
      />
      <PaymentsView payments={rows} doctorRevenue={doctorRevenue} />
    </div>
  );
}

/** Mask a patient ID for display: show first part + **** */
function maskPatientId(id: string): string {
  if (!id) return "—";
  // If it's a human-readable ID like ESP-HX8ZVDGK-1234
  if (id.startsWith("ESP-") || id.startsWith("ESIRI-")) {
    const parts = id.split("-");
    if (parts.length >= 3) {
      return `${parts[0]}-${parts[1]}-****`;
    }
    return id.length > 8 ? id.slice(0, 8) + "****" : id;
  }
  // UUID — show first 8 chars
  return id.slice(0, 8) + "****";
}
