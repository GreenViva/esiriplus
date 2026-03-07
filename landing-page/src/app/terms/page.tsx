"use client";

import PageShell from "@/components/PageShell";
import { t } from "@/lib/i18n";

export default function TermsPage() {
  return (
    <PageShell>
      {(locale) => (
        <div className="section-container section-padding">
          <div className="max-w-3xl mx-auto">
            <h1 className="section-title mb-2">{t("terms.title", locale)}</h1>
            <p className="text-sm text-gray-500 mb-10">
              {t("terms.updated", locale)}: March 7, 2026
            </p>

            <div className="prose prose-gray max-w-none space-y-8 text-gray-600">
              <section>
                <h2 className="text-xl font-bold text-brand-navy mb-3">1. Acceptance of Terms</h2>
                <p className="leading-relaxed">
                  By accessing or using the eSiri mobile application and related services (the &quot;Service&quot;),
                  you agree to be bound by these Terms of Service. If you do not agree to these terms, please
                  do not use our Service.
                </p>
              </section>

              <section>
                <h2 className="text-xl font-bold text-brand-navy mb-3">2. Description of Service</h2>
                <p className="leading-relaxed">
                  eSiri is a telemedicine platform that connects patients with licensed healthcare providers
                  for remote consultations. Our Service includes messaging, audio calls, video calls,
                  appointment scheduling, digital prescriptions, and related healthcare features.
                </p>
                <p className="leading-relaxed mt-3">
                  <strong>Important:</strong> eSiri is not an emergency medical service. If you are
                  experiencing a medical emergency, please call emergency services or visit your nearest
                  hospital immediately.
                </p>
              </section>

              <section>
                <h2 className="text-xl font-bold text-brand-navy mb-3">3. User Accounts</h2>
                <ul className="list-disc pl-6 space-y-2">
                  <li>You must provide accurate and complete information when creating an account</li>
                  <li>You are responsible for maintaining the security of your account credentials</li>
                  <li>You must be at least 18 years old to create an account, or have parental consent</li>
                  <li>One person may only maintain one active account</li>
                  <li>You must not share your account with others</li>
                </ul>
              </section>

              <section>
                <h2 className="text-xl font-bold text-brand-navy mb-3">4. Healthcare Disclaimer</h2>
                <p className="leading-relaxed">
                  The medical advice provided through eSiri is based on the information you provide to
                  healthcare providers during consultations. Doctors on our platform exercise independent
                  medical judgment. eSiri does not practice medicine and does not interfere with the
                  doctor-patient relationship.
                </p>
                <p className="leading-relaxed mt-3">
                  Consultations through eSiri are not a substitute for in-person medical examinations
                  when such examinations are medically necessary. Your doctor may recommend that you
                  seek in-person care when appropriate.
                </p>
              </section>

              <section>
                <h2 className="text-xl font-bold text-brand-navy mb-3">5. Doctor Verification</h2>
                <p className="leading-relaxed">
                  All healthcare providers on our platform undergo a verification process that includes
                  license validation and credential checks. However, eSiri does not guarantee the
                  outcome of any consultation or treatment recommended by doctors on the platform.
                </p>
              </section>

              <section>
                <h2 className="text-xl font-bold text-brand-navy mb-3">6. Payments and Fees</h2>
                <ul className="list-disc pl-6 space-y-2">
                  <li>Consultation fees are displayed before you confirm a booking</li>
                  <li>Payments are processed securely through our payment providers</li>
                  <li>Refund policies are applied on a case-by-case basis</li>
                  <li>Service fees are subject to change with prior notice</li>
                </ul>
              </section>

              <section>
                <h2 className="text-xl font-bold text-brand-navy mb-3">7. Acceptable Use</h2>
                <p className="leading-relaxed">You agree not to:</p>
                <ul className="list-disc pl-6 space-y-2 mt-3">
                  <li>Use the Service for any unlawful purpose</li>
                  <li>Harass, abuse, or threaten healthcare providers or other users</li>
                  <li>Provide false or misleading medical information</li>
                  <li>Attempt to access another user&apos;s account</li>
                  <li>Interfere with the proper functioning of the Service</li>
                  <li>Use the Service to obtain prescriptions fraudulently</li>
                </ul>
              </section>

              <section>
                <h2 className="text-xl font-bold text-brand-navy mb-3">8. Intellectual Property</h2>
                <p className="leading-relaxed">
                  All content, features, and functionality of the eSiri platform are owned by eSiri Health
                  and are protected by copyright, trademark, and other intellectual property laws. You may
                  not reproduce, distribute, or create derivative works without our written permission.
                </p>
              </section>

              <section>
                <h2 className="text-xl font-bold text-brand-navy mb-3">9. Limitation of Liability</h2>
                <p className="leading-relaxed">
                  To the maximum extent permitted by law, eSiri Health shall not be liable for any indirect,
                  incidental, special, consequential, or punitive damages arising out of or related to your
                  use of the Service. Our total liability shall not exceed the amount paid by you for the
                  specific consultation giving rise to the claim.
                </p>
              </section>

              <section>
                <h2 className="text-xl font-bold text-brand-navy mb-3">10. Termination</h2>
                <p className="leading-relaxed">
                  We reserve the right to suspend or terminate your account at any time for violation of
                  these Terms. You may also delete your account at any time. Upon termination, your right
                  to use the Service will immediately cease.
                </p>
              </section>

              <section>
                <h2 className="text-xl font-bold text-brand-navy mb-3">11. Governing Law</h2>
                <p className="leading-relaxed">
                  These Terms shall be governed by and construed in accordance with the laws of the United
                  Republic of Tanzania. Any disputes arising under these Terms shall be resolved in the
                  courts of Dar es Salaam, Tanzania.
                </p>
              </section>

              <section>
                <h2 className="text-xl font-bold text-brand-navy mb-3">12. Changes to Terms</h2>
                <p className="leading-relaxed">
                  We reserve the right to modify these Terms at any time. We will notify users of any
                  material changes through the app or via email. Continued use of the Service after
                  changes constitutes acceptance of the modified Terms.
                </p>
              </section>

              <section>
                <h2 className="text-xl font-bold text-brand-navy mb-3">13. Contact</h2>
                <p className="leading-relaxed">
                  For questions about these Terms of Service, contact us at:
                </p>
                <p className="mt-2">
                  <strong>Email:</strong> legal@esiri.africa<br />
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
