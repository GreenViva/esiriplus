"use client";

import { useState, useEffect } from "react";
import { createClient } from "@/lib/supabase/client";
import { getAllAuthUsers } from "@/lib/adminApi";
import RealtimeRefresh from "@/components/RealtimeRefresh";
import AuditLogView from "./AuditLogView";

export default function HRAuditLogPage() {
  const [enrichedLogs, setEnrichedLogs] = useState<
    {
      id: string;
      admin_id: string;
      action: string;
      target_type: string | null;
      target_id: string | null;
      details: Record<string, unknown> | null;
      created_at: string;
      admin_email: string;
      target_name: string | null;
    }[]
  >([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const supabase = createClient();

    Promise.all([
      supabase
        .from("admin_logs")
        .select("log_id, admin_id, action, target_type, target_id, details, created_at")
        .order("created_at", { ascending: false })
        .limit(200),
      getAllAuthUsers(),
    ]).then(async ([logsRes, authUsersRes]) => {
      const allLogs = (logsRes.data ?? []) as {
        log_id: string;
        admin_id: string;
        action: string;
        target_type: string | null;
        target_id: string | null;
        details: Record<string, unknown> | null;
        created_at: string;
      }[];

      // Build email map from auth users
      const authUsers = authUsersRes.data?.users ?? [];
      const emailMap: Record<string, string> = {};
      for (const u of authUsers) {
        emailMap[u.id] = u.email ?? u.id.slice(0, 8) + "...";
      }

      // Resolve target doctor names where applicable
      const doctorTargetIds = allLogs
        .filter((l) => l.target_type === "doctor_profile" && l.target_id)
        .map((l) => l.target_id!);

      let doctorNameMap: Record<string, string> = {};
      if (doctorTargetIds.length > 0) {
        const { data: doctors } = await supabase
          .from("doctor_profiles")
          .select("doctor_id, full_name")
          .in("doctor_id", doctorTargetIds);

        for (const d of doctors ?? []) {
          doctorNameMap[d.doctor_id] = d.full_name;
        }
      }

      const enriched = allLogs.map((log) => ({
        id: log.log_id,
        admin_id: log.admin_id,
        action: log.action,
        target_type: log.target_type,
        target_id: log.target_id,
        details: log.details,
        created_at: log.created_at,
        admin_email: emailMap[log.admin_id] ?? log.admin_id.slice(0, 8) + "...",
        target_name: log.target_id ? (doctorNameMap[log.target_id] ?? null) : null,
      }));

      setEnrichedLogs(enriched);
      setLoading(false);
    });
  }, []);

  if (loading) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-brand-teal border-t-transparent" />
      </div>
    );
  }

  return (
    <>
      <RealtimeRefresh
        tables={["admin_logs"]}
        channelName="hr-audit-realtime"
      />
      <AuditLogView logs={enrichedLogs} />
    </>
  );
}
