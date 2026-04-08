'use client';

import { Phone, Mail } from 'lucide-react';
import { useTranslation } from '@/hooks/useTranslation';

export default function ContactUs() {
  const { t } = useTranslation();
  return (
    <div className="text-center py-4">
      <p className="text-[13px] font-semibold" style={{ color: 'var(--on-surface-variant, #6B7280)' }}>
        {t('contact_us')}
      </p>
      <div className="flex items-center justify-center gap-4 mt-2">
        <a href="tel:+255663582994" className="flex items-center gap-1.5 text-[12px] font-medium text-[#2A9D8F]">
          <Phone size={14} /> +255 663 582 994
        </a>
        <a href="mailto:support@esiri.africa" className="flex items-center gap-1.5 text-[12px] font-medium text-[#2A9D8F]">
          <Mail size={14} /> support@esiri.africa
        </a>
      </div>
    </div>
  );
}
