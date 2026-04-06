// _shared/auth.ts
// Validates Bearer JWTs on every request.
// Handles TWO types of tokens:
//   1. Patient JWTs — custom HS256 signed by create-patient-session
//      Contains: { sub (session_id), session_id, role: "authenticated", app_role: "patient", exp, iat }
//      (Also supports legacy format: { session_id, session_token, role: "patient", exp, iat })
//   2. Doctor/Admin JWTs — issued by Supabase Auth
//      Contains: { sub (auth.uid), role, exp, iat }

import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const SUPABASE_URL           = Deno.env.get("SUPABASE_URL")!;
const SUPABASE_SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const JWT_SECRET             = Deno.env.get("JWT_SECRET") ?? Deno.env.get("SUPABASE_JWT_SECRET")!;

export interface AuthResult {
  userId: string | null;
  sessionId: string | null;
  role: "doctor" | "patient" | "admin" | "hr" | "finance" | "audit";
  jwt: string;
}

export class AuthError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

// ── Decode JWT payload without verification ───────────────────────────────────
function decodeJwtPayload(jwt: string): Record<string, unknown> {
  try {
    const payloadB64 = jwt.split(".")[1];
    const decoded    = atob(payloadB64.replace(/-/g, "+").replace(/_/g, "/"));
    return JSON.parse(decoded);
  } catch {
    throw new AuthError(401, "Malformed JWT payload");
  }
}

// ── Verify HS256 JWT signature ────────────────────────────────────────────────
async function verifyHS256(jwt: string, secret: string): Promise<boolean> {
  try {
    const parts    = jwt.split(".");
    if (parts.length !== 3) return false;

    const sigInput = `${parts[0]}.${parts[1]}`;
    const sigBytes = Uint8Array.from(
      atob(parts[2].replace(/-/g, "+").replace(/_/g, "/")),
      (c) => c.charCodeAt(0)
    );

    const key = await crypto.subtle.importKey(
      "raw",
      new TextEncoder().encode(secret),
      { name: "HMAC", hash: "SHA-256" },
      false,
      ["verify"]
    );

    return await crypto.subtle.verify(
      "HMAC",
      key,
      sigBytes,
      new TextEncoder().encode(sigInput)
    );
  } catch {
    return false;
  }
}

// ── SHA-256 hex ───────────────────────────────────────────────────────────────
export async function sha256Hex(input: string): Promise<string> {
  const data   = new TextEncoder().encode(input);
  const buffer = await crypto.subtle.digest("SHA-256", data);
  return Array.from(new Uint8Array(buffer))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

// ── Main validator ────────────────────────────────────────────────────────────
export async function validateAuth(req: Request): Promise<AuthResult> {
  // All requests use anon key in Authorization to bypass the Supabase gateway
  // JWT check. The actual user JWT arrives in a custom header:
  //   X-Patient-Token  — custom HS256 JWT for patients
  //   X-Doctor-Token   — Supabase Auth JWT for doctors/admins
  const patientTokenHeader = req.headers.get("X-Patient-Token");
  const doctorTokenHeader  = req.headers.get("X-Doctor-Token");
  const authHeader         = req.headers.get("Authorization");

  let jwt: string;
  let authMethod: "patient-header" | "doctor-header" | "bearer";
  if (patientTokenHeader?.trim()) {
    jwt = patientTokenHeader.trim();
    authMethod = "patient-header";
  } else if (doctorTokenHeader?.trim()) {
    jwt = doctorTokenHeader.trim();
    authMethod = "doctor-header";
  } else if (authHeader?.startsWith("Bearer ")) {
    jwt = authHeader.replace("Bearer ", "").trim();
    authMethod = "bearer";
  } else {
    throw new AuthError(401, "Missing or malformed Authorization header");
  }

  if (!jwt) throw new AuthError(401, "Empty token");

  const claims = decodeJwtPayload(jwt);
  const now    = Math.floor(Date.now() / 1000);

  // Check expiry
  if (claims.exp && (claims.exp as number) < now) {
    throw new AuthError(401, "Token has expired");
  }

  // ── Path A: Patient JWT (app_role:"patient" or legacy role:"patient") ─────
  // Auto-detect patient JWTs regardless of which header they arrived in.
  // This prevents auth failures when the client accidentally sends a patient
  // token via X-Doctor-Token instead of X-Patient-Token.
  const isPatientJwt =
    (claims.app_role === "patient" || claims.role === "patient") &&
    claims.session_id;
  if (isPatientJwt) {
    const sessionId = claims.session_id as string;

    // Verify our custom HS256 signature
    const valid = await verifyHS256(jwt, JWT_SECRET);
    if (!valid) {
      console.warn(`[AUTH] REJECTED patient token — invalid HS256 signature | session_id=${sessionId.slice(0, 8)}...`);
      throw new AuthError(401, "Invalid patient token signature");
    }

    // Verify session exists and is active in DB
    const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);
    const { data: session } = await supabase
      .from("patient_sessions")
      .select("session_id, is_active, is_locked, expires_at")
      .eq("session_id", sessionId)
      .single();

    if (!session) {
      console.warn(`[AUTH] REJECTED patient token — session not found | session_id=${sessionId.slice(0, 8)}...`);
      throw new AuthError(401, "Session not found");
    }
    if (!session.is_active) {
      console.warn(`[AUTH] REJECTED patient token — inactive session | session_id=${sessionId.slice(0, 8)}...`);
      throw new AuthError(401, "Session is not active");
    }
    if (session.is_locked) {
      console.warn(`[AUTH] REJECTED patient token — locked session | session_id=${sessionId.slice(0, 8)}...`);
      throw new AuthError(401, "Session is locked");
    }
    if (new Date(session.expires_at) < new Date()) {
      console.warn(`[AUTH] REJECTED patient token — expired session | session_id=${sessionId.slice(0, 8)}...`);
      throw new AuthError(401, "Session has expired");
    }

    // Log patient auth for monitoring (no PII — session ID is opaque)
    if (authMethod === "patient-header") {
      console.log(`[AUTH] Patient auth via X-Patient-Token | session=${sessionId.slice(0, 8)}...`);
    }

    return {
      userId:       null,
      sessionId:    sessionId,
      role:         "patient",
      jwt,
    };
  }

  // ── Path B: Supabase Auth JWT (doctor/admin) ──────────────────────────────
  const supabase = createClient(SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY);
  const { data: { user }, error } = await supabase.auth.getUser(jwt);

  if (error || !user) throw new AuthError(401, "Invalid or expired token");

  // Determine role from user_roles table
  let role: AuthResult["role"] = "doctor";
  const { data: roleData } = await supabase
    .from("user_roles")
    .select("role_name")
    .eq("user_id", user.id)
    .limit(1)
    .single();

  if (roleData) {
    role = roleData.role_name as AuthResult["role"];
  }

  return {
    userId:       user.id,
    sessionId:    null,
    role,
    jwt,
  };
}

/** Require a specific role — throws 403 if not met */
export function requireRole(
  auth: AuthResult,
  ...allowed: AuthResult["role"][]
): void {
  if (!allowed.includes(auth.role)) {
    throw new AuthError(403, `Insufficient permissions. Required: ${allowed.join(" or ")}`);
  }
}
