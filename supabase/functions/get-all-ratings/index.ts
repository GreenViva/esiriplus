// functions/get-all-ratings/index.ts
// Fetch all doctor ratings with search, filter, and pagination.
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

    const search = (typeof body.search === "string" ? body.search : "").trim().toLowerCase();
    const ratingFilter = typeof body.rating_filter === "number" ? body.rating_filter : null;
    const flaggedOnly = body.flagged_only === true;
    const limit = Math.min(Number(body.limit) || 50, 200);
    const offset = Number(body.offset) || 0;

    const supabase = getServiceClient();

    // Build the ratings query with a join to doctor_profiles for the doctor name
    let query = supabase
      .from("doctor_ratings")
      .select(
        "rating_id, doctor_id, rating, comment, created_at, is_flagged, doctor_profiles!inner(full_name)",
        { count: "exact" }
      )
      .order("created_at", { ascending: false });

    if (ratingFilter !== null && ratingFilter >= 1 && ratingFilter <= 5) {
      query = query.eq("rating", ratingFilter);
    }

    if (flaggedOnly) {
      query = query.eq("is_flagged", true);
    }

    if (search) {
      // Filter by doctor name or comment using OR
      query = query.or(
        `comment.ilike.%${search}%,doctor_profiles.full_name.ilike.%${search}%`
      );
    }

    // Apply pagination
    const { data: ratings, error, count } = await query.range(offset, offset + limit - 1);

    if (error) throw error;

    // Get total flagged count (separate lightweight query)
    const { count: flaggedCount } = await supabase
      .from("doctor_ratings")
      .select("rating_id", { count: "exact", head: true })
      .eq("is_flagged", true);

    // Flatten the joined doctor_profiles into the response
    const formattedRatings = (ratings ?? []).map((r: any) => ({
      rating_id: r.rating_id,
      doctor_id: r.doctor_id,
      doctor_name: r.doctor_profiles?.full_name ?? "Unknown Doctor",
      rating: r.rating,
      comment: r.comment,
      created_at: r.created_at,
      is_flagged: r.is_flagged ?? false,
    }));

    return successResponse(
      {
        ratings: formattedRatings,
        total_count: count ?? 0,
        flagged_count: flaggedCount ?? 0,
      },
      200,
      origin
    );
  } catch (err) {
    return errorResponse(err, origin);
  }
});
