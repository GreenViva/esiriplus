import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Privacy Policy",
  description:
    "eSiri's Privacy Policy explains how we collect, use, and protect your personal and health information.",
};

export default function Layout({ children }: { children: React.ReactNode }) {
  return children;
}
