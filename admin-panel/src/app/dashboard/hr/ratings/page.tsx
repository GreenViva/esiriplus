export const dynamic = "force-dynamic";

import { createAdminClient } from "@/lib/supabase/admin";
import RealtimeRefresh from "@/components/RealtimeRefresh";
import RatingsView from "./RatingsView";

export default async function HRRatingsPage() {
  const supabase = createAdminClient();

  const { data: ratings } = await supabase
    .from("doctor_ratings")
    .select(
      "rating_id, doctor_id, consultation_id, patient_session_id, rating, comment, is_flagged, flagged_by, flagged_at, created_at, doctor_profiles(full_name)"
    )
    .order("created_at", { ascending: false })
    .limit(200);

  const allRatings = (ratings ?? []) as unknown as {
    rating_id: string;
    doctor_id: string;
    consultation_id: string;
    patient_session_id: string;
    rating: number;
    comment: string | null;
    is_flagged: boolean;
    flagged_by: string | null;
    flagged_at: string | null;
    created_at: string;
    doctor_profiles: { full_name: string } | null;
  }[];

  return (
    <>
      <RealtimeRefresh
        tables={["doctor_ratings"]}
        channelName="hr-ratings-realtime"
      />
      <RatingsView ratings={allRatings} />
    </>
  );
}
