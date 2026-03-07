"use client";

import PageShell from "@/components/PageShell";
import { t } from "@/lib/i18n";

export default function PrivacyPage() {
  return (
    <PageShell>
      {(locale) => (
        <div className="section-container section-padding">
          <div className="max-w-3xl mx-auto">
            <h1 className="section-title mb-2">{t("privacy.title", locale)}</h1>
            <p className="text-sm text-gray-500 mb-10">
              {t("privacy.updated", locale)}: March 7, 2026
            </p>

            <div className="prose prose-gray max-w-none space-y-8 text-gray-600">
              <section>
                <h2 className="text-xl font-bold text-brand-navy mb-3">1. Introduction</h2>
                <p className="leading-relaxed">
                  eSiri Health (&quot;we&quot;, &quot;us&quot;, &quot;our&quot;) is committed to protecting
                  your personal information and your right to privacy. This Privacy Policy explains how we
                  collect, use, disclose, and safeguard your information when you use our mobile application
                  and related services.
                </p>
              </section>

              <section>
                <h2 className="text-xl font-bold text-brand-navy mb-3">2. Information We Collect</h2>
                <p className="leading-relaxed mb-3">We collect the following types of information:</p>
                <ul className="list-disc pl-6 space-y-2">
                  <li><strong>Personal Information:</strong> Name, email address, phone number, date of birth, and gender provided during account registration.</li>
                  <li><strong>Health Information:</strong> Medical history, symptoms, consultation records, prescriptions, and diagnoses shared during consultations.</li>
                  <li><strong>Payment Information:</strong> Transaction details processed through our secure payment providers. We do not store full payment card details.</li>
                  <li><strong>Device Information:</strong> Device type, operating system, unique device identifiers, and usage data for service improvement.</li>
                  <li><strong>Communication Data:</strong> Messages, audio, and video consultation recordings (where consented) for quality assurance.</li>
                </ul>
              </section>

              <section>
                <h2 className="text-xl font-bold text-brand-navy mb-3">3. How We Use Your Information</h2>
                <ul className="list-disc pl-6 space-y-2">
                  <li>To facilitate healthcare consultations between patients and doctors</li>
                  <li>To verify doctor credentials and maintain platform quality</li>
                  <li>To process payments and manage your account</li>
                  <li>To send important updates about your consultations and prescriptions</li>
                  <li>To improve our services through anonymized analytics</li>
                  <li>To comply with legal obligations and healthcare regulations</li>
                </ul>
              </section>

              <section>
                <h2 className="text-xl font-bold text-brand-navy mb-3">4. Data Security</h2>
                <p className="leading-relaxed">
                  We implement industry-standard security measures to protect your data, including:
                </p>
                <ul className="list-disc pl-6 space-y-2 mt-3">
                  <li>End-to-end encryption for all communications between patients and doctors</li>
                  <li>Encrypted data storage using AES-256 encryption</li>
                  <li>Secure authentication with multi-factor verification</li>
                  <li>Regular security audits and vulnerability assessments</li>
                  <li>Strict access controls limiting data access to authorized personnel only</li>
                </ul>
              </section>

              <section>
                <h2 className="text-xl font-bold text-brand-navy mb-3">5. Data Sharing</h2>
                <p className="leading-relaxed">
                  We do not sell your personal data. We share information only in the following circumstances:
                </p>
                <ul className="list-disc pl-6 space-y-2 mt-3">
                  <li>With your consulting doctor, to facilitate your healthcare</li>
                  <li>With payment processors, to complete transactions</li>
                  <li>With law enforcement, when required by law</li>
                  <li>With service providers who assist in operating our platform, under strict confidentiality agreements</li>
                </ul>
              </section>

              <section>
                <h2 className="text-xl font-bold text-brand-navy mb-3">6. Your Rights</h2>
                <p className="leading-relaxed">You have the right to:</p>
                <ul className="list-disc pl-6 space-y-2 mt-3">
                  <li>Access your personal data</li>
                  <li>Request correction of inaccurate data</li>
                  <li>Request deletion of your data (subject to legal retention requirements)</li>
                  <li>Withdraw consent for data processing</li>
                  <li>Export your health records</li>
                </ul>
              </section>

              <section>
                <h2 className="text-xl font-bold text-brand-navy mb-3">7. Data Retention</h2>
                <p className="leading-relaxed">
                  We retain your personal data for as long as your account is active or as needed to provide
                  our services. Medical records are retained in accordance with applicable healthcare
                  regulations. You may request deletion of your account and associated data at any time.
                </p>
              </section>

              <section>
                <h2 className="text-xl font-bold text-brand-navy mb-3">8. Children&apos;s Privacy</h2>
                <p className="leading-relaxed">
                  Our services are not directed to children under 18 without parental consent. If you are a
                  parent and believe your child has provided personal information, please contact us.
                </p>
              </section>

              <section>
                <h2 className="text-xl font-bold text-brand-navy mb-3">9. Changes to This Policy</h2>
                <p className="leading-relaxed">
                  We may update this Privacy Policy from time to time. We will notify you of any changes by
                  posting the updated policy on this page and updating the &quot;Last updated&quot; date.
                </p>
              </section>

              <section>
                <h2 className="text-xl font-bold text-brand-navy mb-3">10. Contact Us</h2>
                <p className="leading-relaxed">
                  If you have any questions about this Privacy Policy, please contact us at:
                </p>
                <p className="mt-2">
                  <strong>Email:</strong> privacy@esiri.africa<br />
                  <strong>Address:</strong> Dar es Salaam, Tanzania
                </p>
              </section>
            </div>
          </div>
        </div>
      )}
    </PageShell>
  );
}
