#!/usr/bin/env node
// One-off SQL helper using service-role PostgREST.
//
// Usage:  node scripts/supabase-sql.mjs <query-name> [arg=value ...]
//
// Reads creds from .env.local (SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY).
// Each "query-name" corresponds to a hand-written case below — we are
// NOT exposing arbitrary SQL from the CLI, because PostgREST is not a SQL
// shell. For one-off bespoke queries, add a new case or call REST endpoints
// directly.

import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, "..");

// ── Load .env.local ──────────────────────────────────────────────────────
const envText = fs.readFileSync(path.join(repoRoot, ".env.local"), "utf8");
const env = Object.fromEntries(
  envText
    .split(/\r?\n/)
    .filter((l) => l && !l.startsWith("#") && l.includes("="))
    .map((l) => {
      const i = l.indexOf("=");
      return [l.slice(0, i).trim(), l.slice(i + 1).trim()];
    }),
);

const URL_BASE = env.SUPABASE_URL?.replace(/\/+$/, "");
const KEY = env.SUPABASE_SERVICE_ROLE_KEY;
if (!URL_BASE || !KEY) {
  console.error("Missing SUPABASE_URL or SUPABASE_SERVICE_ROLE_KEY in .env.local");
  process.exit(1);
}

// ── REST helper ──────────────────────────────────────────────────────────
async function rest(pathname, opts = {}) {
  const url = `${URL_BASE}/rest/v1/${pathname}`;
  const res = await fetch(url, {
    ...opts,
    headers: {
      apikey: KEY,
      Authorization: `Bearer ${KEY}`,
      "Content-Type": "application/json",
      Prefer: opts.prefer ?? "return=representation",
      ...(opts.headers ?? {}),
    },
  });
  const txt = await res.text();
  if (!res.ok) {
    throw new Error(`REST ${res.status}: ${txt.slice(0, 300)}`);
  }
  return txt ? JSON.parse(txt) : null;
}

// ── Queries ──────────────────────────────────────────────────────────────
const QUERIES = {
  /**
   * All verified, non-banned, not-suspended doctors with no fcm_tokens row.
   * These are the doctors who were silently missing pushes — the Gordian
   * cohort that the auto-flag trigger was wrongly punishing before the
   * 20260424100000 migration.
   */
  "doctors-without-tokens": async () => {
    const [doctors, tokens] = await Promise.all([
      rest(
        "doctor_profiles?is_verified=eq.true&is_banned=eq.false&select=doctor_id,full_name,specialty,last_heartbeat_at,is_available,flagged,flag_reason,suspended_until,updated_at",
      ),
      rest("fcm_tokens?select=user_id"),
    ]);
    const tokenSet = new Set(tokens.map((t) => t.user_id));
    const now = Date.now();
    const rows = doctors.filter((d) => {
      if (tokenSet.has(d.doctor_id)) return false;
      if (d.suspended_until && new Date(d.suspended_until).getTime() > now) return false;
      return true;
    });
    console.log(`Found ${rows.length} verified doctors with no fcm_tokens row:`);
    for (const r of rows) {
      const since = r.last_heartbeat_at
        ? `${Math.round((now - new Date(r.last_heartbeat_at).getTime()) / 60000)} min ago`
        : "never";
      console.log(
        `  ${r.doctor_id}  ${r.full_name?.padEnd(28) ?? "—"}  ${(r.specialty ?? "").padEnd(14)}  available=${r.is_available}  flagged=${r.flagged}  heartbeat=${since}`,
      );
    }
  },

  /**
   * Count consultation_requests by status for a given doctor.
   * Usage: node scripts/supabase-sql.mjs doctor-request-counts doctor_id=<uuid>
   */
  "doctor-request-counts": async (args) => {
    const id = args.doctor_id;
    if (!id) throw new Error("doctor_id=<uuid> required");
    const rows = await rest(
      `consultation_requests?doctor_id=eq.${id}&select=status`,
    );
    const counts = rows.reduce((acc, r) => {
      acc[r.status] = (acc[r.status] ?? 0) + 1;
      return acc;
    }, {});
    console.log(`consultation_requests for doctor ${id}:`);
    for (const [status, n] of Object.entries(counts).sort((a, b) => b[1] - a[1])) {
      console.log(`  ${status.padEnd(12)}  ${n}`);
    }
    console.log(`  ${"TOTAL".padEnd(12)}  ${rows.length}`);
  },

  /**
   * Full doctor state: flags, suspension, heartbeat, FCM row.
   * Usage: node scripts/supabase-sql.mjs doctor-status doctor_id=<uuid>
   */
  "doctor-status": async (args) => {
    const id = args.doctor_id;
    if (!id) throw new Error("doctor_id=<uuid> required");
    const [[d], tokens] = await Promise.all([
      rest(
        `doctor_profiles?doctor_id=eq.${id}&select=doctor_id,full_name,specialty,is_verified,is_available,is_banned,flagged,flag_reason,suspended_until,last_heartbeat_at,in_session,updated_at`,
      ),
      rest(`fcm_tokens?user_id=eq.${id}&select=user_id,token,updated_at`),
    ]);
    if (!d) {
      console.log(`No doctor found with id ${id}`);
      return;
    }
    console.log(JSON.stringify(d, null, 2));
    console.log(`fcm_tokens rows: ${tokens.length}`);
    for (const t of tokens) {
      console.log(`  token ${t.token?.slice(0, 24)}…  updated_at=${t.updated_at}`);
    }
  },
};

// ── Dispatch ─────────────────────────────────────────────────────────────
const [, , name, ...rawArgs] = process.argv;
if (!name || !QUERIES[name]) {
  console.error(`Available queries: ${Object.keys(QUERIES).join(", ")}`);
  process.exit(1);
}
const args = Object.fromEntries(rawArgs.map((a) => a.split("=", 2)));
try {
  await QUERIES[name](args);
} catch (e) {
  console.error(e.message);
  process.exit(1);
}
