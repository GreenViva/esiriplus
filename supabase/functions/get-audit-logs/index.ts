// functions/get-audit-logs/index.ts
// Fetch audit log entries with search, filter, and pagination.
// Auth: admin, hr, audit only. Rate limit: LIMITS.read.

import { handlePreflight } from "../_shared/cors.ts";
import { validateAuth, requireRole } from "../_shared/auth.ts";
import { errorResponse, successResponse } from "../_shared/errors.ts";
import { getServiceClient } from "../_shared/supabase.ts";
import { LIMITS } from "../_shared/rateLimit.ts";

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    const auth = await validateAuth(req);
    requireRole(auth, "admin", "hr", "audit");

    await LIMITS.read(auth.userId!);

    const body = await req.json();

    const search = (typeof body.search === "string" ? body.search : "").trim();
    const limit = Math.min(Number(body.limit) || 100, 200);
    const offset = Number(body.offset) || 0;

    const supabase = getServiceClient();

    // Build query
    let query = supabase
      .from("admin_logs")
      .select(
        "id, admin_id, action, target_type, target_id, details, created_at",
        { count: "exact" }
      )
      .order("created_at", { ascending: false });

    // Server-side search on action and target_type
    if (search) {
      query = query.or(
        `action.ilike.%${search}%,target_type.ilike.%${search}%`
      );
    }

    // Apply pagination
    const { data: logs, error, count } = await query.range(
      offset,
      offset + limit - 1
    );

    if (error) throw error;

    // Resolve admin IDs to emails
    const adminIds = [
      ...new Set(
        (logs ?? []).map((l: any) => l.admin_id).filter(Boolean)
      ),
    ];

    const emailMap: Record<string, string> = {};
    if (adminIds.length > 0) {
      const {
        data: { users },
      } = await supabase.auth.admin.listUsers({ perPage: 1000 });
      for (const u of users ?? []) {
        emailMap[u.id] = u.email ?? u.id.slice(0, 8) + "...";
      }
    }

    // Resolve doctor target names where applicable
    const doctorTargetIds = (logs ?? [])
      .filter(
        (l: any) => l.target_type === "doctor_profile" && l.target_id
      )
      .map((l: any) => l.target_id!);

    const doctorNameMap: Record<string, string> = {};
    if (doctorTargetIds.length > 0) {
      const { data: doctors } = await supabase
        .from("doctor_profiles")
        .select("doctor_id, full_name")
        .in("doctor_id", doctorTargetIds);

      for (const d of doctors ?? []) {
        doctorNameMap[d.doctor_id] = d.full_name;
      }
    }

    // Enrich logs with resolved names
    const enrichedLogs = (logs ?? []).map((log: any) => ({
      id: log.id,
      admin_id: log.admin_id,
      admin_email:
        emailMap[log.admin_id] ?? log.admin_id?.slice(0, 8) + "...",
      action: log.action,
      target_type: log.target_type,
      target_id: log.target_id,
      target_name: log.target_id
        ? doctorNameMap[log.target_id] ?? null
        : null,
      details: log.details,
      created_at: log.created_at,
    }));

    return successResponse(
      {
        logs: enrichedLogs,
        total_count: count ?? 0,
      },
      200,
      origin
    );
  } catch (err) {
    return errorResponse(err, origin);
  }
});
