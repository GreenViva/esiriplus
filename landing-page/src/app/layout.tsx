import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  metadataBase: new URL("https://esiri.africa"),
  title: {
    default: "eSiri — Healthcare Consultation, Anywhere in Tanzania",
    template: "%s | eSiri",
  },
  description:
    "Connect with verified doctors through secure messaging, voice, or video consultations. Tanzania's trusted telemedicine platform for accessible healthcare.",
  keywords: [
    "telemedicine",
    "Tanzania",
    "healthcare",
    "doctor consultation",
    "medical app",
    "eSiri",
    "online doctor",
    "video consultation",
    "health platform",
    "East Africa",
  ],
  authors: [{ name: "eSiri Health" }],
  creator: "eSiri Health",
  openGraph: {
    type: "website",
    locale: "en_US",
    url: "https://esiri.africa",
    siteName: "eSiri",
    title: "eSiri — Healthcare Consultation, Anywhere in Tanzania",
    description:
      "Connect with verified doctors through secure messaging, voice, or video consultations.",
    images: [
      {
        url: "/og-image.png",
        width: 1200,
        height: 630,
        alt: "eSiri - Telemedicine Platform",
      },
    ],
  },
  twitter: {
    card: "summary_large_image",
    title: "eSiri — Healthcare Consultation, Anywhere in Tanzania",
    description:
      "Connect with verified doctors through secure messaging, voice, or video consultations.",
    images: ["/og-image.png"],
  },
  robots: {
    index: true,
    follow: true,
    googleBot: { index: true, follow: true },
  },
  alternates: {
    canonical: "https://esiri.africa",
  },
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" className="scroll-smooth">
      <head>
        <link rel="icon" href="/favicon.ico" sizes="any" />
        <link rel="apple-touch-icon" href="/apple-touch-icon.png" />
        <meta name="theme-color" content="#2A9D8F" />
      </head>
      <body className="min-h-screen flex flex-col">{children}</body>
    </html>
  );
}
