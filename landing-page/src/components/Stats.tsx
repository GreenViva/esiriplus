import type { Locale } from "@/lib/i18n";
import { t } from "@/lib/i18n";

interface StatsProps {
  locale?: Locale;
}

const stats = [
  { value: "50+", labelKey: "stats.doctors" as const },
  { value: "2,000+", labelKey: "stats.consultations" as const },
  { value: "5,000+", labelKey: "stats.patients" as const },
  { value: "4.8", labelKey: "stats.rating" as const },
];

export default function Stats({ locale = "en" }: StatsProps) {
  return (
    <section className="bg-brand-navy py-14 sm:py-16">
      <div className="section-container">
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-8 lg:gap-12">
          {stats.map((stat) => (
            <div key={stat.labelKey} className="text-center">
              <p className="text-3xl sm:text-4xl font-extrabold text-white mb-2">
                {stat.value}
              </p>
              <p className="text-sm text-white/70 font-medium">
                {t(stat.labelKey, locale)}
              </p>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}
