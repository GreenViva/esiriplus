"use client";

import { useState } from "react";
import type { Locale } from "@/lib/i18n";
import Header from "@/components/Header";
import Footer from "@/components/Footer";

interface PageShellProps {
  children: (locale: Locale) => React.ReactNode;
}

export default function PageShell({ children }: PageShellProps) {
  const [locale, setLocale] = useState<Locale>("en");

  return (
    <>
      <Header locale={locale} onLocaleChange={setLocale} />
      <main className="flex-1 pt-20">{children(locale)}</main>
      <Footer locale={locale} />
    </>
  );
}
