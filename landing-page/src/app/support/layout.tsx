import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Support",
  description:
    "Get help with eSiri. Find answers to frequently asked questions or contact our support team.",
};

export default function Layout({ children }: { children: React.ReactNode }) {
  return children;
}
