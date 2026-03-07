"use client";

import { useState } from "react";
import type { Locale } from "@/lib/i18n";
import { t } from "@/lib/i18n";

interface FAQProps {
  locale?: Locale;
}

const faqKeys = [
  { q: "faq.q1" as const, a: "faq.a1" as const },
  { q: "faq.q2" as const, a: "faq.a2" as const },
  { q: "faq.q3" as const, a: "faq.a3" as const },
  { q: "faq.q4" as const, a: "faq.a4" as const },
  { q: "faq.q5" as const, a: "faq.a5" as const },
  { q: "faq.q6" as const, a: "faq.a6" as const },
];

export default function FAQ({ locale = "en" }: FAQProps) {
  const [open, setOpen] = useState<number | null>(null);

  return (
    <section id="faq" className="section-padding bg-gray-50">
      <div className="section-container">
        <div className="max-w-3xl mx-auto">
          <h2 className="section-title text-center mb-12">
            {t("faq.title", locale)}
          </h2>

          <div className="space-y-3">
            {faqKeys.map((item, idx) => (
              <div
                key={item.q}
                className="bg-white rounded-2xl border border-gray-100 overflow-hidden"
              >
                <button
                  onClick={() => setOpen(open === idx ? null : idx)}
                  className="w-full flex items-center justify-between p-5 sm:p-6 text-left hover:bg-gray-50/50 transition-colors"
                  aria-expanded={open === idx}
                >
                  <span className="font-semibold text-brand-navy pr-4">
                    {t(item.q, locale)}
                  </span>
                  <svg
                    className={`w-5 h-5 text-gray-400 flex-shrink-0 transition-transform duration-200 ${
                      open === idx ? "rotate-180" : ""
                    }`}
                    fill="none"
                    viewBox="0 0 24 24"
                    stroke="currentColor"
                    strokeWidth={2}
                  >
                    <path strokeLinecap="round" strokeLinejoin="round" d="m19.5 8.25-7.5 7.5-7.5-7.5" />
                  </svg>
                </button>
                {open === idx && (
                  <div className="px-5 sm:px-6 pb-5 sm:pb-6">
                    <p className="text-gray-600 leading-relaxed">
                      {t(item.a, locale)}
                    </p>
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      </div>
    </section>
  );
}
