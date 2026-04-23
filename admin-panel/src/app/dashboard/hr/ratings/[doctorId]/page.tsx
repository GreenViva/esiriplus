import DoctorRatingsDetail from "../DoctorRatingsDetail";

export default async function DoctorRatingsDetailPage({
  params,
}: {
  params: Promise<{ doctorId: string }>;
}) {
  const { doctorId } = await params;
  return <DoctorRatingsDetail doctorId={doctorId} backHref="/dashboard/hr/ratings" />;
}
