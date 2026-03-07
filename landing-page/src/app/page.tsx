"use client";

import { useState } from "react";
import type { Locale } from "@/lib/i18n";
import Header from "@/components/Header";
import Hero from "@/components/Hero";
import About from "@/components/About";
import Features from "@/components/Features";
import Stats from "@/components/Stats";
import ForDoctors from "@/components/ForDoctors";
import Trust from "@/components/Trust";
import AppDownload from "@/components/AppDownload";
import FAQ from "@/components/FAQ";
import Contact from "@/components/Contact";
import Footer from "@/components/Footer";

export default function HomePage() {
  const [locale, setLocale] = useState<Locale>("en");

  return (
    <>
      <Header locale={locale} onLocaleChange={setLocale} />
      <main className="flex-1">
        <Hero locale={locale} />
        <About locale={locale} />
        <Stats locale={locale} />
        <Features locale={locale} />
        <ForDoctors locale={locale} />
        <Trust locale={locale} />
        <AppDownload locale={locale} />
        <FAQ locale={locale} />
        <Contact locale={locale} />
      </main>
      <Footer locale={locale} />
    </>
  );
}
