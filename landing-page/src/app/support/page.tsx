"use client";

import PageShell from "@/components/PageShell";
import FAQ from "@/components/FAQ";
import Contact from "@/components/Contact";
import { t } from "@/lib/i18n";

export default function SupportPage() {
  return (
    <PageShell>
      {(locale) => (
        <>
          <div className="section-container pt-8 pb-4">
            <h1 className="section-title">{t("support.page.title", locale)}</h1>
            <p className="section-subtitle mt-3">
              {t("contact.subtitle", locale)}
            </p>
          </div>
          <FAQ locale={locale} />
          <Contact locale={locale} />
        </>
      )}
    </PageShell>
  );
}
