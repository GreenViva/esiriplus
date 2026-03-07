"use client";

import { useState } from "react";
import PageShell from "@/components/PageShell";
import { t } from "@/lib/i18n";
import type { Locale } from "@/lib/i18n";

const specialties = [
  "apply.specialties.general",
  "apply.specialties.pediatrics",
  "apply.specialties.obstetrics",
  "apply.specialties.internal",
  "apply.specialties.dermatology",
  "apply.specialties.psychiatry",
  "apply.specialties.cardiology",
  "apply.specialties.other",
] as const;

function DoctorApplicationForm({ locale }: { locale: Locale }) {
  const [form, setForm] = useState({
    fullName: "",
    email: "",
    phone: "",
    license: "",
    specialty: "",
    experience: "",
    bio: "",
  });
  const [submitted, setSubmitted] = useState(false);
  const [sending, setSending] = useState(false);

  function update(field: string, value: string) {
    setForm((prev) => ({ ...prev, [field]: value }));
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSending(true);
    // Simulate submission — replace with real endpoint
    await new Promise((r) => setTimeout(r, 1200));
    setSending(false);
    setSubmitted(true);
  }

  if (submitted) {
    return (
      <div className="text-center py-16">
        <div className="w-20 h-20 bg-green-100 rounded-full flex items-center justify-center mx-auto mb-6">
          <svg className="w-10 h-10 text-green-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M9 12.75 11.25 15 15 9.75M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z" />
          </svg>
        </div>
        <h2 className="text-2xl font-bold text-brand-navy mb-3">Application Submitted</h2>
        <p className="text-gray-600 max-w-md mx-auto leading-relaxed">
          {t("apply.success", locale)}
        </p>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-6">
      <div className="grid sm:grid-cols-2 gap-6">
        <div>
          <label htmlFor="apply-name" className="block text-sm font-medium text-gray-700 mb-1.5">
            {t("apply.fullname", locale)} *
          </label>
          <input
            id="apply-name"
            type="text"
            required
            value={form.fullName}
            onChange={(e) => update("fullName", e.target.value)}
            className="w-full px-4 py-3 rounded-xl border border-gray-200 bg-white text-sm text-gray-900 focus:outline-none focus:ring-2 focus:ring-brand-teal/30 focus:border-brand-teal transition-colors"
          />
        </div>
        <div>
          <label htmlFor="apply-email" className="block text-sm font-medium text-gray-700 mb-1.5">
            {t("apply.email", locale)} *
          </label>
          <input
            id="apply-email"
            type="email"
            required
            value={form.email}
            onChange={(e) => update("email", e.target.value)}
            className="w-full px-4 py-3 rounded-xl border border-gray-200 bg-white text-sm text-gray-900 focus:outline-none focus:ring-2 focus:ring-brand-teal/30 focus:border-brand-teal transition-colors"
          />
        </div>
      </div>

      <div className="grid sm:grid-cols-2 gap-6">
        <div>
          <label htmlFor="apply-phone" className="block text-sm font-medium text-gray-700 mb-1.5">
            {t("apply.phone", locale)} *
          </label>
          <input
            id="apply-phone"
            type="tel"
            required
            value={form.phone}
            onChange={(e) => update("phone", e.target.value)}
            placeholder="+255"
            className="w-full px-4 py-3 rounded-xl border border-gray-200 bg-white text-sm text-gray-900 placeholder:text-gray-400 focus:outline-none focus:ring-2 focus:ring-brand-teal/30 focus:border-brand-teal transition-colors"
          />
        </div>
        <div>
          <label htmlFor="apply-license" className="block text-sm font-medium text-gray-700 mb-1.5">
            {t("apply.license", locale)} *
          </label>
          <input
            id="apply-license"
            type="text"
            required
            value={form.license}
            onChange={(e) => update("license", e.target.value)}
            className="w-full px-4 py-3 rounded-xl border border-gray-200 bg-white text-sm text-gray-900 focus:outline-none focus:ring-2 focus:ring-brand-teal/30 focus:border-brand-teal transition-colors"
          />
        </div>
      </div>

      <div className="grid sm:grid-cols-2 gap-6">
        <div>
          <label htmlFor="apply-specialty" className="block text-sm font-medium text-gray-700 mb-1.5">
            {t("apply.specialty", locale)} *
          </label>
          <select
            id="apply-specialty"
            required
            value={form.specialty}
            onChange={(e) => update("specialty", e.target.value)}
            className="w-full px-4 py-3 rounded-xl border border-gray-200 bg-white text-sm text-gray-900 focus:outline-none focus:ring-2 focus:ring-brand-teal/30 focus:border-brand-teal transition-colors"
          >
            <option value="">--</option>
            {specialties.map((key) => (
              <option key={key} value={key}>
                {t(key, locale)}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="apply-experience" className="block text-sm font-medium text-gray-700 mb-1.5">
            {t("apply.experience", locale)} *
          </label>
          <input
            id="apply-experience"
            type="number"
            min="0"
            max="60"
            required
            value={form.experience}
            onChange={(e) => update("experience", e.target.value)}
            className="w-full px-4 py-3 rounded-xl border border-gray-200 bg-white text-sm text-gray-900 focus:outline-none focus:ring-2 focus:ring-brand-teal/30 focus:border-brand-teal transition-colors"
          />
        </div>
      </div>

      <div>
        <label htmlFor="apply-bio" className="block text-sm font-medium text-gray-700 mb-1.5">
          {t("apply.bio", locale)}
        </label>
        <textarea
          id="apply-bio"
          rows={4}
          value={form.bio}
          onChange={(e) => update("bio", e.target.value)}
          className="w-full px-4 py-3 rounded-xl border border-gray-200 bg-white text-sm text-gray-900 focus:outline-none focus:ring-2 focus:ring-brand-teal/30 focus:border-brand-teal transition-colors resize-none"
        />
      </div>

      <button
        type="submit"
        disabled={sending}
        className="btn-primary w-full sm:w-auto disabled:opacity-50"
      >
        {sending ? (
          <svg className="animate-spin h-4 w-4" viewBox="0 0 24 24" fill="none">
            <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
            <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z" />
          </svg>
        ) : null}
        {t("apply.submit", locale)}
      </button>
    </form>
  );
}

export default function DoctorsPage() {
  return (
    <PageShell>
      {(locale) => (
        <div className="section-container section-padding">
          <div className="max-w-3xl mx-auto">
            <span className="section-tag">{t("platform.doctors.heading", locale)}</span>
            <h1 className="section-title mt-4 mb-3">{t("apply.title", locale)}</h1>
            <p className="section-subtitle mb-10">
              {t("apply.subtitle", locale)}
            </p>

            <div className="bg-white rounded-2xl border border-gray-100 shadow-sm p-6 sm:p-10">
              <DoctorApplicationForm locale={locale} />
            </div>
          </div>
        </div>
      )}
    </PageShell>
  );
}
