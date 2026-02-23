export const dynamic = "force-dynamic";

import { createAdminClient } from "@/lib/supabase/admin";
import PaymentsView, { type PaymentRow } from "./PaymentsView";
import RealtimeRefresh from "@/components/RealtimeRefresh";

const PAGE_SIZE = 50;

interface Props {
  searchParams: Promise<{ filter?: string; page?: string }>;
}

export default async function PaymentsPage({ searchParams }: Props) {
  const { page: pageStr } = await searchParams;
  const page = Math.max(1, parseInt(pageStr ?? "1", 10) || 1);
  const supabase = createAdminClient();

  // Get total count
  const { count: totalCount } = await supabase
    .from("payments")
    .select("*", { count: "exact", head: true });

  // Paginated fetch
  const from = (page - 1) * PAGE_SIZE;
  const to = from + PAGE_SIZE - 1;

  const { data } = await supabase
    .from("payments")
    .select("payment_id, amount, currency, payment_type, payment_method, status, created_at, service_access_payments(service_type)")
    .order("created_at", { ascending: false })
    .range(from, to);

  const initialPayments = (data ?? []) as unknown as PaymentRow[];
  const totalPages = Math.ceil((totalCount ?? 0) / PAGE_SIZE);

  return (
    <div>
      <RealtimeRefresh tables={["payments", "service_access_payments"]} channelName="admin-payments-realtime" />
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">Payments</h1>
        <p className="text-sm text-gray-400 mt-0.5">
          Track all payment transactions across the platform
        </p>
      </div>
      <PaymentsView initialPayments={initialPayments} currentPage={page} totalPages={totalPages} />
    </div>
  );
}
