import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Join as a Doctor",
  description:
    "Apply to join eSiri as a healthcare provider. Expand your patient reach, set flexible schedules, and grow your practice on Tanzania's leading telemedicine platform.",
};

export default function Layout({ children }: { children: React.ReactNode }) {
  return children;
}
