import { handlePreflight } from "../_shared/cors.ts";
import { validateAuth, requireRole } from "../_shared/auth.ts";
import {
  errorResponse,
  successResponse,
  ValidationError,
} from "../_shared/errors.ts";
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
    const doctorId = body.doctor_id as string;
    if (!doctorId) {
      throw new ValidationError("doctor_id is required");
    }

    const limit = Math.min(Number(body.limit) || 50, 200);
    const offset = Number(body.offset) || 0;

    const supabase = getServiceClient();

    // Fetch individual ratings (paginated)
    const { data: ratings, error } = await supabase
      .from("doctor_ratings")
      .select(
        "rating_id, rating, comment, created_at, is_flagged, flagged_by, flagged_at"
      )
      .eq("doctor_id", doctorId)
      .order("created_at", { ascending: false })
      .range(offset, offset + limit - 1);

    if (error) throw error;

    // Fetch all ratings for distribution calculation
    const { data: allRatings } = await supabase
      .from("doctor_ratings")
      .select("rating")
      .eq("doctor_id", doctorId);

    const distribution: Record<number, number> = {
      1: 0,
      2: 0,
      3: 0,
      4: 0,
      5: 0,
    };
    let totalRatings = 0;
    let sumRatings = 0;

    if (allRatings) {
      for (const r of allRatings) {
        distribution[r.rating] = (distribution[r.rating] || 0) + 1;
        sumRatings += r.rating;
        totalRatings++;
      }
    }

    const averageRating =
      totalRatings > 0
        ? Math.round((sumRatings / totalRatings) * 10) / 10
        : 0;

    return successResponse(
      {
        doctor_id: doctorId,
        average_rating: averageRating,
        total_ratings: totalRatings,
        distribution,
        ratings: ratings ?? [],
      },
      200,
      origin
    );
  } catch (err) {
    return errorResponse(err, origin);
  }
});
