import DoctorRatingsDetail from "../../hr/ratings/DoctorRatingsDetail";

export default async function DoctorRatingsDetailPage({
  params,
}: {
  params: Promise<{ doctorId: string }>;
}) {
  const { doctorId } = await params;
  return <DoctorRatingsDetail doctorId={doctorId} backHref="/dashboard/ratings" />;
}
