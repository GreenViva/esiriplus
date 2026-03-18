import { getServiceClient } from "../_shared/supabase.ts";
import { corsHeaders, handlePreflight } from "../_shared/cors.ts";
import {
  errorResponse,
  successResponse,
  requireMethod,
} from "../_shared/errors.ts";

const SUPABASE_ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY") ?? "";

/**
 * Receives a batch of performance metrics from the Android app
 * and inserts them into the performance_metrics table.
 *
 * Gated by anon-key check — the client must send the project anon key
 * in the apikey header to prove it is a legitimate app instance.
 */
Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return handlePreflight(req);
  requireMethod(req, "POST");

  const origin = req.headers.get("origin") ?? "";

  // Verify the caller is a legitimate app instance via the anon key
  const apikey = req.headers.get("apikey") ?? "";
  if (!SUPABASE_ANON_KEY || apikey !== SUPABASE_ANON_KEY) {
    return new Response(
      JSON.stringify({ error: "Unauthorized" }),
      { status: 401, headers: { ...corsHeaders(origin), "Content-Type": "application/json" } },
    );
  }

  try {
    const body = await req.json();
    const metrics: unknown[] = Array.isArray(body) ? body : body.metrics;

    if (!Array.isArray(metrics) || metrics.length === 0) {
      return errorResponse(
        { status: 400, message: "Expected a non-empty array of metrics" },
        origin,
      );
    }

    // Cap batch size to prevent abuse
    const batch = metrics.slice(0, 200);

    const VALID_TYPES = new Set([
      "api_response",
      "edge_function",
      "db_query",
      "app_event",
    ]);

    // Validate and sanitize each entry
    const rows = batch
      .filter((m: any) => {
        return (
          typeof m.endpoint === "string" &&
          typeof m.latency_ms === "number" &&
          m.latency_ms >= 0 &&
          m.latency_ms < 300_000 &&
          VALID_TYPES.has(m.metric_type)
        );
      })
      .map((m: any) => ({
        metric_type: m.metric_type,
        endpoint: String(m.endpoint).slice(0, 255),
        method: m.method ? String(m.method).slice(0, 10) : null,
        status_code:
          typeof m.status_code === "number" ? m.status_code : null,
        latency_ms: Math.round(m.latency_ms),
        success: m.success !== false,
        error_type: m.error_type ? String(m.error_type).slice(0, 50) : null,
        app_version: m.app_version
          ? String(m.app_version).slice(0, 20)
          : null,
        platform: m.platform ? String(m.platform).slice(0, 20) : "android",
      }));

    if (rows.length === 0) {
      return successResponse({ inserted: 0 }, 200, origin);
    }

    const supabase = getServiceClient();
    const { error } = await supabase
      .from("performance_metrics")
      .insert(rows);

    if (error) {
      console.error("Insert error:", error);
      return errorResponse(
        { status: 500, message: "Failed to insert metrics" },
        origin,
      );
    }

    return successResponse({ inserted: rows.length }, 200, origin);
  } catch (e) {
    console.error("log-performance-metrics error:", e);
    return errorResponse(e, origin);
  }
});
