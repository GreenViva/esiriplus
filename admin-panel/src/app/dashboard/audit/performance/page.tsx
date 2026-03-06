import { createAdminClient } from "@/lib/supabase/admin";
import PerformanceDashboard from "@/components/PerformanceDashboard";

export const dynamic = "force-dynamic";

export default async function SystemPerformancePage() {
  const supabase = createAdminClient();

  // Fetch 7 days of hourly-aggregated metrics via Postgres function.
  // The client component filters by the selected time range.
  const { data: stats, error } = await supabase.rpc("get_performance_stats", {
    p_hours_ago: 168,
  });

  if (error) {
    console.error("Failed to fetch performance stats:", error);
  }

  return (
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">
          System Performance
        </h1>
        <p className="text-sm text-gray-400 mt-0.5">
          Platform health, response times, and infrastructure monitoring
        </p>
      </div>

      <PerformanceDashboard stats={stats ?? []} />
    </div>
  );
}
