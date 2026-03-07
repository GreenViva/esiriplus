import type { Locale } from "@/lib/i18n";
import { t } from "@/lib/i18n";

interface AboutProps {
  locale?: Locale;
}

const cards = [
  {
    titleKey: "about.card1.title" as const,
    textKey: "about.card1.text" as const,
    icon: (
      <svg className="w-7 h-7" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M12 21a9.004 9.004 0 0 0 8.716-6.747M12 21a9.004 9.004 0 0 1-8.716-6.747M12 21c2.485 0 4.5-4.03 4.5-9S14.485 3 12 3m0 18c-2.485 0-4.5-4.03-4.5-9S9.515 3 12 3m0 0a8.997 8.997 0 0 1 7.843 4.582M12 3a8.997 8.997 0 0 0-7.843 4.582m15.686 0A11.953 11.953 0 0 1 12 10.5c-2.998 0-5.74-1.1-7.843-2.918m15.686 0A8.959 8.959 0 0 1 21 12c0 .778-.099 1.533-.284 2.253m0 0A17.919 17.919 0 0 1 12 16.5c-3.162 0-6.133-.815-8.716-2.247m0 0A9.015 9.015 0 0 1 3 12c0-1.605.42-3.113 1.157-4.418" />
      </svg>
    ),
  },
  {
    titleKey: "about.card2.title" as const,
    textKey: "about.card2.text" as const,
    icon: (
      <svg className="w-7 h-7" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75 11.25 15 15 9.75m-3-7.036A11.959 11.959 0 0 1 3.598 6 11.99 11.99 0 0 0 3 9.749c0 5.592 3.824 10.29 9 11.623 5.176-1.332 9-6.03 9-11.622 0-1.31-.21-2.571-.598-3.751h-.152c-3.196 0-6.1-1.248-8.25-3.285Z" />
      </svg>
    ),
  },
  {
    titleKey: "about.card3.title" as const,
    textKey: "about.card3.text" as const,
    icon: (
      <svg className="w-7 h-7" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M3.75 13.5l10.5-11.25L12 10.5h8.25L9.75 21.75 12 13.5H3.75Z" />
      </svg>
    ),
  },
];

export default function About({ locale = "en" }: AboutProps) {
  return (
    <section id="about" className="section-padding bg-white">
      <div className="section-container">
        <div className="max-w-3xl mx-auto text-center mb-14">
          <span className="section-tag">{t("about.tag", locale)}</span>
          <h2 className="section-title mt-4 mb-5">
            {t("about.title", locale)}
          </h2>
          <p className="section-subtitle mx-auto">
            {t("about.description", locale)}
          </p>
        </div>

        <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-6 lg:gap-8">
          {cards.map((card) => (
            <div key={card.titleKey} className="card group text-center">
              <div className="w-14 h-14 bg-brand-teal-light text-brand-teal rounded-2xl flex items-center justify-center mx-auto mb-5 group-hover:bg-brand-teal group-hover:text-white transition-colors duration-200">
                {card.icon}
              </div>
              <h3 className="text-lg font-bold text-brand-navy mb-3">
                {t(card.titleKey, locale)}
              </h3>
              <p className="text-gray-600 leading-relaxed">
                {t(card.textKey, locale)}
              </p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
