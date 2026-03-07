"use client";

import { Suspense } from "react";
import DoctorDetailClient from "./DoctorDetailClient";

export default function DoctorDetailPage() {
  return (
    <Suspense fallback={<div className="flex items-center justify-center py-20"><p className="text-gray-400">Loading...</p></div>}>
      <DoctorDetailClient />
    </Suspense>
  );
}
