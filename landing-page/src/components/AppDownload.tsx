import type { Locale } from "@/lib/i18n";
import { t } from "@/lib/i18n";

interface AppDownloadProps {
  locale?: Locale;
}

export default function AppDownload({ locale = "en" }: AppDownloadProps) {
  return (
    <section id="download" className="section-padding bg-gradient-to-br from-brand-teal to-brand-teal-dark relative overflow-hidden">
      {/* Background pattern */}
      <div className="absolute inset-0 opacity-10">
        <svg className="w-full h-full" xmlns="http://www.w3.org/2000/svg">
          <defs>
            <pattern id="dl-grid" x="0" y="0" width="60" height="60" patternUnits="userSpaceOnUse">
              <circle cx="1" cy="1" r="1" fill="white" />
            </pattern>
          </defs>
          <rect width="100%" height="100%" fill="url(#dl-grid)" />
        </svg>
      </div>

      <div className="section-container relative">
        <div className="grid lg:grid-cols-2 gap-12 items-center">
          {/* Text */}
          <div className="text-center lg:text-left">
            <span className="inline-block px-4 py-1.5 bg-white/20 text-white text-xs font-semibold tracking-wider uppercase rounded-full mb-6">
              {t("download.tag", locale)}
            </span>
            <h2 className="text-3xl sm:text-4xl lg:text-5xl font-bold text-white leading-tight mb-5">
              {t("download.title", locale)}
            </h2>
            <p className="text-lg text-white/80 mb-8 max-w-md mx-auto lg:mx-0">
              {t("download.subtitle", locale)}
            </p>

            <div className="flex flex-col sm:flex-row items-center gap-4 justify-center lg:justify-start">
              {/* Google Play button */}
              <a
                href="#"
                className="inline-flex items-center gap-3 bg-black text-white px-6 py-3.5 rounded-xl hover:bg-gray-900 transition-colors"
                aria-label="Download from Google Play"
              >
                <svg className="w-7 h-7" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M3.609 1.814L13.792 12 3.61 22.186a.996.996 0 0 1-.61-.92V2.734a1 1 0 0 1 .609-.92zm10.89 10.893l2.302 2.302-10.937 6.333 8.635-8.635zm3.199-3.199l2.302 2.302c.7.4.7 1.08 0 1.48l-2.302 1.302L15.396 12l2.302-2.492zM5.864 2.658L16.8 8.99l-2.302 2.302L5.864 2.658z" />
                </svg>
                <div className="text-left">
                  <p className="text-[10px] leading-none opacity-80">GET IT ON</p>
                  <p className="text-base font-semibold leading-tight mt-0.5">Google Play</p>
                </div>
              </a>

              {/* App Store (coming soon) */}
              <div className="inline-flex items-center gap-3 bg-white/10 text-white/60 px-6 py-3.5 rounded-xl cursor-not-allowed border border-white/20">
                <svg className="w-7 h-7" viewBox="0 0 24 24" fill="currentColor">
                  <path d="M18.71 19.5c-.83 1.24-1.71 2.45-3.05 2.47-1.34.03-1.77-.79-3.29-.79-1.53 0-2 .77-3.27.82-1.31.05-2.3-1.32-3.14-2.53C4.25 17 2.94 12.45 4.7 9.39c.87-1.52 2.43-2.48 4.12-2.51 1.28-.02 2.5.87 3.29.87.78 0 2.26-1.07 3.8-.91.65.03 2.47.26 3.64 1.98-.09.06-2.17 1.28-2.15 3.81.03 3.02 2.65 4.03 2.68 4.04-.03.07-.42 1.44-1.38 2.83M13 3.5c.73-.83 1.94-1.46 2.94-1.5.13 1.17-.34 2.35-1.04 3.19-.69.85-1.83 1.51-2.95 1.42-.15-1.15.41-2.35 1.05-3.11Z" />
                </svg>
                <div className="text-left">
                  <p className="text-[10px] leading-none opacity-60">COMING SOON</p>
                  <p className="text-base font-semibold leading-tight mt-0.5">App Store</p>
                </div>
              </div>
            </div>
          </div>

          {/* Phone mockup */}
          <div className="flex justify-center lg:justify-end">
            <div className="relative">
              <div className="w-56 sm:w-64 bg-white rounded-[2rem] p-3 shadow-2xl shadow-black/20">
                <div className="bg-gray-50 rounded-[1.5rem] overflow-hidden">
                  <div className="bg-brand-teal px-5 py-4">
                    <div className="flex items-center justify-between mb-3">
                      <span className="text-white text-sm font-bold">eSiri</span>
                      <div className="w-6 h-6 bg-white/20 rounded-full" />
                    </div>
                    <p className="text-white/90 text-xs">Your health, one tap away</p>
                  </div>
                  <div className="p-4 space-y-3">
                    <div className="bg-white rounded-xl p-3 shadow-sm">
                      <div className="flex items-center gap-2 mb-2">
                        <div className="w-8 h-8 bg-brand-teal/10 rounded-full" />
                        <div className="flex-1">
                          <div className="h-2.5 bg-gray-200 rounded w-3/4" />
                          <div className="h-2 bg-gray-100 rounded w-1/2 mt-1" />
                        </div>
                      </div>
                    </div>
                    <div className="bg-white rounded-xl p-3 shadow-sm">
                      <div className="flex items-center gap-2 mb-2">
                        <div className="w-8 h-8 bg-amber-50 rounded-full" />
                        <div className="flex-1">
                          <div className="h-2.5 bg-gray-200 rounded w-2/3" />
                          <div className="h-2 bg-gray-100 rounded w-1/3 mt-1" />
                        </div>
                      </div>
                    </div>
                    <div className="h-10 bg-brand-teal rounded-xl flex items-center justify-center">
                      <span className="text-white text-xs font-semibold">Book Now</span>
                    </div>
                  </div>
                </div>
              </div>

              {/* QR Code placeholder */}
              <div className="absolute -bottom-4 -left-4 bg-white rounded-xl shadow-lg p-3 border border-gray-100">
                <div className="w-16 h-16 bg-gray-100 rounded-lg flex items-center justify-center">
                  <svg className="w-8 h-8 text-gray-400" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                    <path strokeLinecap="round" strokeLinejoin="round" d="M3.75 4.875c0-.621.504-1.125 1.125-1.125h4.5c.621 0 1.125.504 1.125 1.125v4.5c0 .621-.504 1.125-1.125 1.125h-4.5A1.125 1.125 0 0 1 3.75 9.375v-4.5ZM3.75 14.625c0-.621.504-1.125 1.125-1.125h4.5c.621 0 1.125.504 1.125 1.125v4.5c0 .621-.504 1.125-1.125 1.125h-4.5a1.125 1.125 0 0 1-1.125-1.125v-4.5ZM13.5 4.875c0-.621.504-1.125 1.125-1.125h4.5c.621 0 1.125.504 1.125 1.125v4.5c0 .621-.504 1.125-1.125 1.125h-4.5A1.125 1.125 0 0 1 13.5 9.375v-4.5Z" />
                    <path strokeLinecap="round" strokeLinejoin="round" d="M6.75 6.75h.75v.75h-.75v-.75ZM6.75 16.5h.75v.75h-.75v-.75ZM16.5 6.75h.75v.75h-.75v-.75ZM13.5 13.5h.75v.75h-.75v-.75ZM13.5 19.5h.75v.75h-.75v-.75ZM19.5 13.5h.75v.75h-.75v-.75ZM19.5 19.5h.75v.75h-.75v-.75ZM16.5 16.5h.75v.75h-.75v-.75Z" />
                  </svg>
                </div>
                <p className="text-[8px] text-gray-400 text-center mt-1">{t("download.scan", locale)}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
