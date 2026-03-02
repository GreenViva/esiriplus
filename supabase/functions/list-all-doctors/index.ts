// list-all-doctors – Returns ALL doctor profiles for admin management.
// Includes summary stats by verification status.
//
// Auth: admin, hr only.
// Rate limit: 30/min (read).

import { handlePreflight } from "../_shared/cors.ts";
import { validateAuth, requireRole, type AuthResult } from "../_shared/auth.ts";
import { LIMITS } from "../_shared/rateLimit.ts";
import {
  errorResponse,
  successResponse,
} from "../_shared/errors.ts";
import { logEvent } from "../_shared/logger.ts";
import { getServiceClient } from "../_shared/supabase.ts";

Deno.serve(async (req: Request) => {
  const origin = req.headers.get("origin");
  const preflight = handlePreflight(req);
  if (preflight) return preflight;

  try {
    const auth = await validateAuth(req);
    requireRole(auth, "admin", "hr");
    await LIMITS.read(auth.userId!);

    const supabase = getServiceClient();

    const { data, error } = await supabase
      .from("doctor_profiles")
      .select(
        "doctor_id, full_name, email, phone, specialty, specialist_field, " +
        "languages, bio, license_number, years_experience, profile_photo_url, " +
        "average_rating, total_ratings, is_verified, is_available, services, " +
        "country_code, country, is_banned, banned_at, ban_reason, " +
        "suspended_until, suspension_reason, rejection_reason, " +
        "verification_status, warning_message, warning_at, created_at, updated_at"
      )
      .order("created_at", { ascending: false });

    if (error) {
      throw error;
    }

    const doctors = data ?? [];

    const now = new Date();
    const stats = {
      total: doctors.length,
      pending: 0,
      active: 0,
      suspended: 0,
      rejected: 0,
      banned: 0,
    };

    for (const doc of doctors) {
      if (doc.is_banned) {
        stats.banned++;
      } else if (
        doc.suspended_until &&
        new Date(doc.suspended_until) > now
      ) {
        stats.suspended++;
      } else if (!doc.is_verified && doc.rejection_reason) {
        stats.rejected++;
      } else if (!doc.is_verified) {
        stats.pending++;
      } else {
        stats.active++;
      }
    }

    await logEvent({
      function_name: "list-all-doctors",
      level: "info",
      user_id: auth.userId,
      action: "list_all_doctors",
      metadata: { total: stats.total },
    });

    return successResponse({ doctors, stats }, 200, origin);
  } catch (err) {
    await logEvent({
      function_name: "list-all-doctors",
      level: "error",
      action: "list_all_doctors_failed",
      error_message: err instanceof Error ? err.message : String(err),
    });
    return errorResponse(err, origin);
  }
});
