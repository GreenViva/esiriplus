// functions/get-admin-stats/index.ts
// Returns lightweight platform stats for the admin dashboard.
// Revenue, patients, consultations, doctor earnings — no AI calls.
// Auth: admin/hr/audit OR internal service key.

import { handlePreflight } from "../_shared/cors.ts";
import { validateAuth, requireRole } from "../_shared/auth.ts";
import { LIMITS } from "../_shared/rateLimit.ts";
import { errorResponse, successResponse } from "../_shared/errors.ts";
import { logEvent, getClientIp } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    // Allow internal service key bypass (for testing), otherwise require admin auth
    const serviceKey = req.headers.get("X-Service-Key");
    const expectedKey = Deno.env.get("INTERNAL_SERVICE_KEY") ?? Deno.env.get("SUPABASE_SERVICE_ROLE_KEY");
    const isServiceCall = serviceKey && expectedKey && serviceKey === expectedKey;

    if (!isServiceCall) {
      const auth = await validateAuth(req);
      requireRole(auth, "admin", "hr", "audit");
      await LIMITS.read(auth.userId ?? "anon");
    }

    const supabase = getServiceClient();

    // Run all queries in parallel for speed
    const [
      paymentsRes,
      servicePaymentsRes,
      callPaymentsRes,
      earningsRes,
      patientsRes,
      consultationsRes,
      completedConsultationsRes,
    ] = await Promise.all([
      // Main payments table
      supabase
        .from("payments")
        .select("amount, status"),

      // Service access payments (completed purchases)
      supabase
        .from("service_access_payments")
        .select("amount, status"),

      // Call recharge payments
      supabase
        .from("call_recharge_payments")
        .select("amount, status"),

      // Doctor earnings
      supabase
        .from("doctor_earnings")
        .select("amount, status"),

      // Total unique patients
      supabase
        .from("patient_sessions")
        .select("session_id", { count: "exact", head: true }),

      // Total consultations
      supabase
        .from("consultations")
        .select("consultation_id", { count: "exact", head: true }),

      // Completed consultations
      supabase
        .from("consultations")
        .select("consultation_id", { count: "exact", head: true })
        .eq("status", "completed"),
    ]);

    console.log(`[get-admin-stats] payments query: rows=${paymentsRes.data?.length ?? 0}, error=${paymentsRes.error?.message ?? "none"}`);
    console.log(`[get-admin-stats] service_access_payments: rows=${servicePaymentsRes.data?.length ?? 0}, error=${servicePaymentsRes.error?.message ?? "none"}`);
    console.log(`[get-admin-stats] call_recharge_payments: rows=${callPaymentsRes.data?.length ?? 0}, error=${callPaymentsRes.error?.message ?? "none"}`);
    console.log(`[get-admin-stats] doctor_earnings: rows=${earningsRes.data?.length ?? 0}, error=${earningsRes.error?.message ?? "none"}`);
    console.log(`[get-admin-stats] patients count=${patientsRes.count ?? 0}, error=${patientsRes.error?.message ?? "none"}`);
    console.log(`[get-admin-stats] consultations count=${consultationsRes.count ?? 0}, error=${consultationsRes.error?.message ?? "none"}`);

    // Calculate total revenue from main payments table (completed/paid only)
    const payments = paymentsRes.data ?? [];
    const completedPayments = payments.filter(
      (p) => p.status === "completed" || p.status === "paid"
    );
    const mainRevenue = completedPayments.reduce(
      (sum: number, p: any) => sum + (Number(p.amount) || 0),
      0
    );

    // Also sum from service_access_payments and call_recharge_payments
    // These are downstream records created by mpesa-callback after payment confirmation.
    const servicePayments = (servicePaymentsRes.data ?? []).filter(
      (p) => p.status === "completed" || p.status === "paid"
    );
    const serviceRevenue = servicePayments.reduce(
      (sum: number, p: any) => sum + (Number(p.amount) || 0),
      0
    );

    const callPayments = (callPaymentsRes.data ?? []).filter(
      (p) => p.status === "completed" || p.status === "paid"
    );
    const callRevenue = callPayments.reduce(
      (sum: number, p: any) => sum + (Number(p.amount) || 0),
      0
    );

    // Use main payments table revenue as primary, fall back to sub-tables, then doctor_earnings
    const paymentRevenue = mainRevenue > 0 ? mainRevenue : (serviceRevenue + callRevenue);

    console.log(`[get-admin-stats] mainRevenue=${mainRevenue}, serviceRevenue=${serviceRevenue}, callRevenue=${callRevenue}, paymentRevenue=${paymentRevenue}`);

    const pendingPayments = payments.filter((p) => p.status === "pending");
    const pendingRevenue = pendingPayments.reduce(
      (sum: number, p: any) => sum + (Number(p.amount) || 0),
      0
    );

    // Calculate doctor earnings
    const earnings = earningsRes.data ?? [];
    const totalDoctorEarnings = earnings.reduce(
      (sum: number, e: any) => sum + (Number(e.amount) || 0),
      0
    );
    const paidEarnings = earnings
      .filter((e) => e.status === "PAID" || e.status === "paid")
      .reduce((sum: number, e: any) => sum + (Number(e.amount) || 0), 0);

    console.log(`[get-admin-stats] doctorEarnings: total=${totalDoctorEarnings}, paid=${paidEarnings}`);

    // Log a sample payment for debugging if any exist
    if (payments.length > 0) {
      console.log(`[get-admin-stats] Sample payment:`, JSON.stringify(payments[0]));
    }
    if (servicePayments.length > 0) {
      console.log(`[get-admin-stats] Sample service payment:`, JSON.stringify(servicePayments[0]));
    }

    // Total platform revenue: use payment records if available, otherwise
    // derive from doctor_earnings. Doctor earnings = 50% commission, so
    // the full consultation revenue is double the doctor's earnings.
    const totalRevenue = paymentRevenue > 0 ? paymentRevenue : (totalDoctorEarnings * 2);
    const platformCommission = totalRevenue - totalDoctorEarnings;

    console.log(`[get-admin-stats] totalRevenue=${totalRevenue}, platformCommission=${platformCommission}`);

    const stats = {
      revenue: {
        total: totalRevenue,
        platform_commission: platformCommission,
        from_payments: paymentRevenue,
        pending: pendingRevenue,
        currency: "TZS",
        completed_payments: completedPayments.length + servicePayments.length + callPayments.length,
        pending_payments: pendingPayments.length,
        total_payments: payments.length,
      },
      doctor_earnings: {
        total: totalDoctorEarnings,
        paid: paidEarnings,
        unpaid: totalDoctorEarnings - paidEarnings,
      },
      patients: {
        total: patientsRes.count ?? 0,
      },
      consultations: {
        total: consultationsRes.count ?? 0,
        completed: completedConsultationsRes.count ?? 0,
      },
    };

    try {
      await logEvent({
        function_name: "get-admin-stats",
        level: "info",
        user_id: null,
        action: "stats_fetched",
        metadata: {
          total_revenue: totalRevenue,
          total_earnings: totalDoctorEarnings,
          main_payments: payments.length,
          service_payments: servicePayments.length,
          call_payments: callPayments.length,
        },
        ip_address: getClientIp(req),
      });
    } catch (_) { /* swallow */ }

    return successResponse({ stats }, 200, origin);

  } catch (err) {
    console.error(`[get-admin-stats] ERROR:`, err instanceof Error ? err.message : String(err));
    try {
      await logEvent({
        function_name: "get-admin-stats",
        level: "error",
        action: "stats_fetch_failed",
        error_message: err instanceof Error ? err.message : String(err),
      });
    } catch (_) { /* swallow logging failure */ }
    return errorResponse(err, origin);
  }
});
