"use client";

import { useState, useEffect, useCallback } from "react";
import { createClient } from "@/lib/supabase/client";
import PerformanceDashboard from "@/components/PerformanceDashboard";
import RoleGuard from "@/components/RoleGuard";
import type { PerformanceStat } from "@/lib/types/database";

export default function SystemPerformancePage() {
  const [stats, setStats] = useState<PerformanceStat[]>([]);
  const [loading, setLoading] = useState(true);

  const fetchData = useCallback(() => {
    const supabase = createClient();

    supabase
      .rpc("get_performance_stats", { p_hours_ago: 168 })
      .then(({ data, error }) => {
        if (error) {
          console.error("Failed to fetch performance stats:", error);
        }
        setStats(data ?? []);
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
    <RoleGuard allowed={["admin", "audit"]}>
    <div>
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">
          System Performance
        </h1>
        <p className="text-sm text-gray-400 mt-0.5">
          Platform health, response times, and infrastructure monitoring
        </p>
      </div>

      <PerformanceDashboard stats={stats} onRefresh={fetchData} />
    </div>
    </RoleGuard>
  );
}
