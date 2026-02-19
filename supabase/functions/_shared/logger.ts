// _shared/logger.ts
// Writes structured audit events to the admin_logs table.
// Uses service role so it bypasses RLS.

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const supabase = createClient(
  Deno.env.get("SUPABASE_URL")!,
  Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
);

export type LogLevel = "info" | "warn" | "error";

export interface LogEntry {
  function_name: string;
  level: LogLevel;
  user_id?: string | null;
  session_id?: string | null;
  action: string;
  metadata?: Record<string, unknown>;
  error_message?: string;
  ip_address?: string | null;
}

export async function logEvent(entry: LogEntry): Promise<void> {
  try {
    await supabase.from("admin_logs").insert({
      function_name: entry.function_name,
      level: entry.level,
      user_id: entry.user_id ?? null,
      session_id: entry.session_id ?? null,
      action: entry.action,
      metadata: entry.metadata ?? {},
      error_message: entry.error_message ?? null,
      ip_address: entry.ip_address ?? null,
      created_at: new Date().toISOString(),
    });
  } catch (err) {
    // Logging must never crash the main function
    console.error("Logger failed:", err);
  }
}

export function getClientIp(req: Request): string | null {
  return (
    req.headers.get("x-forwarded-for")?.split(",")[0]?.trim() ??
    req.headers.get("cf-connecting-ip") ??
    null
  );
}
