"use client";

import PageShell from "@/components/PageShell";
import { t } from "@/lib/i18n";
import type { Metadata } from "next";

export default function AboutPage() {
  return (
    <PageShell>
      {(locale) => (
        <div className="section-container section-padding">
          <div className="max-w-3xl mx-auto">
            <h1 className="section-title mb-8">{t("about.page.title", locale)}</h1>

            <div className="prose prose-lg max-w-none space-y-6 text-gray-600">
              <section>
                <h2 className="text-2xl font-bold text-brand-navy mt-8 mb-4">Our Mission</h2>
                <p className="leading-relaxed">
                  eSiri is a digital health platform built to make quality healthcare accessible to every
                  Tanzanian, regardless of their location. We believe that access to professional medical
                  advice should not be limited by geography, transportation, or wait times.
                </p>
                <p className="leading-relaxed">
                  Our platform connects patients with verified, licensed doctors for real-time consultations
                  through secure messaging, audio calls, and video calls. Whether you are in Dar es Salaam
                  or in a remote village, eSiri brings healthcare to your fingertips.
                </p>
              </section>

              <section>
                <h2 className="text-2xl font-bold text-brand-navy mt-8 mb-4">Patient Anonymity — Your Privacy, Your Power</h2>
                <div className="bg-brand-teal-light/40 border border-brand-teal/20 rounded-2xl p-6 mb-4">
                  <p className="leading-relaxed text-gray-800 font-medium mb-4">
                    At eSiri, we believe that no one should ever have to avoid seeking medical help because
                    of fear, stigma, or embarrassment. That is why patient anonymity is at the core of everything
                    we build.
                  </p>
                  <ul className="space-y-3">
                    <li className="flex gap-3">
                      <svg className="w-5 h-5 text-brand-teal flex-shrink-0 mt-0.5" fill="currentColor" viewBox="0 0 20 20">
                        <path fillRule="evenodd" d="M16.403 12.652a3 3 0 0 0 0-5.304 3 3 0 0 0-3.75-3.751 3 3 0 0 0-5.305 0 3 3 0 0 0-3.751 3.75 3 3 0 0 0 0 5.305 3 3 0 0 0 3.75 3.751 3 3 0 0 0 5.305 0 3 3 0 0 0 3.751-3.75Zm-2.546-4.46a.75.75 0 0 0-1.214-.882l-3.483 4.79-1.88-1.88a.75.75 0 1 0-1.06 1.061l2.5 2.5a.75.75 0 0 0 1.137-.089l4-5.5Z" clipRule="evenodd" />
                      </svg>
                      <span><strong>Consult without revealing your identity.</strong> Patients can seek medical advice without sharing their real name, photo, or personal details with the doctor.</span>
                    </li>
                    <li className="flex gap-3">
                      <svg className="w-5 h-5 text-brand-teal flex-shrink-0 mt-0.5" fill="currentColor" viewBox="0 0 20 20">
                        <path fillRule="evenodd" d="M16.403 12.652a3 3 0 0 0 0-5.304 3 3 0 0 0-3.75-3.751 3 3 0 0 0-5.305 0 3 3 0 0 0-3.751 3.75 3 3 0 0 0 0 5.305 3 3 0 0 0 3.75 3.751 3 3 0 0 0 5.305 0 3 3 0 0 0 3.751-3.75Zm-2.546-4.46a.75.75 0 0 0-1.214-.882l-3.483 4.79-1.88-1.88a.75.75 0 1 0-1.06 1.061l2.5 2.5a.75.75 0 0 0 1.137-.089l4-5.5Z" clipRule="evenodd" />
                      </svg>
                      <span><strong>No stigma, no judgment.</strong> Whether it is a sensitive condition, mental health concern, or a topic you find difficult to discuss in person — eSiri gives you a safe space to talk to a doctor openly.</span>
                    </li>
                    <li className="flex gap-3">
                      <svg className="w-5 h-5 text-brand-teal flex-shrink-0 mt-0.5" fill="currentColor" viewBox="0 0 20 20">
                        <path fillRule="evenodd" d="M16.403 12.652a3 3 0 0 0 0-5.304 3 3 0 0 0-3.75-3.751 3 3 0 0 0-5.305 0 3 3 0 0 0-3.751 3.75 3 3 0 0 0 0 5.305 3 3 0 0 0 3.75 3.751 3 3 0 0 0 5.305 0 3 3 0 0 0 3.751-3.75Zm-2.546-4.46a.75.75 0 0 0-1.214-.882l-3.483 4.79-1.88-1.88a.75.75 0 1 0-1.06 1.061l2.5 2.5a.75.75 0 0 0 1.137-.089l4-5.5Z" clipRule="evenodd" />
                      </svg>
                      <span><strong>End-to-end encrypted conversations.</strong> Your chats, calls, and medical records are encrypted so only you and your doctor can access them.</span>
                    </li>
                    <li className="flex gap-3">
                      <svg className="w-5 h-5 text-brand-teal flex-shrink-0 mt-0.5" fill="currentColor" viewBox="0 0 20 20">
                        <path fillRule="evenodd" d="M16.403 12.652a3 3 0 0 0 0-5.304 3 3 0 0 0-3.75-3.751 3 3 0 0 0-5.305 0 3 3 0 0 0-3.751 3.75 3 3 0 0 0 0 5.305 3 3 0 0 0 3.75 3.751 3 3 0 0 0 5.305 0 3 3 0 0 0 3.751-3.75Zm-2.546-4.46a.75.75 0 0 0-1.214-.882l-3.483 4.79-1.88-1.88a.75.75 0 1 0-1.06 1.061l2.5 2.5a.75.75 0 0 0 1.137-.089l4-5.5Z" clipRule="evenodd" />
                      </svg>
                      <span><strong>You control your data.</strong> You decide what to share, when to share it, and can request deletion of your records at any time.</span>
                    </li>
                  </ul>
                </div>
                <p className="leading-relaxed">
                  We believe anonymity in healthcare is not just a feature — it is a fundamental right. Many
                  people in Tanzania and across East Africa avoid seeking help for conditions they feel
                  embarrassed about. eSiri removes that barrier entirely, empowering patients to take charge
                  of their health without fear.
                </p>
              </section>

              <section>
                <h2 className="text-2xl font-bold text-brand-navy mt-8 mb-4">For Patients and Doctors</h2>
                <p className="leading-relaxed mb-4">
                  eSiri serves both sides of the healthcare equation. Patients gain anonymous, instant access
                  to verified professionals. Doctors gain a modern platform to expand their practice, reach
                  more patients, set flexible schedules, and earn a reputation through ratings — all within a
                  secure, professional environment.
                </p>
              </section>

              <section>
                <h2 className="text-2xl font-bold text-brand-navy mt-8 mb-4">What We Do</h2>
                <ul className="space-y-3">
                  <li className="flex gap-3">
                    <svg className="w-5 h-5 text-brand-teal flex-shrink-0 mt-0.5" fill="currentColor" viewBox="0 0 20 20">
                      <path fillRule="evenodd" d="M16.403 12.652a3 3 0 0 0 0-5.304 3 3 0 0 0-3.75-3.751 3 3 0 0 0-5.305 0 3 3 0 0 0-3.751 3.75 3 3 0 0 0 0 5.305 3 3 0 0 0 3.75 3.751 3 3 0 0 0 5.305 0 3 3 0 0 0 3.751-3.75Zm-2.546-4.46a.75.75 0 0 0-1.214-.882l-3.483 4.79-1.88-1.88a.75.75 0 1 0-1.06 1.061l2.5 2.5a.75.75 0 0 0 1.137-.089l4-5.5Z" clipRule="evenodd" />
                    </svg>
                    <span>Provide instant access to verified medical professionals through our mobile platform</span>
                  </li>
                  <li className="flex gap-3">
                    <svg className="w-5 h-5 text-brand-teal flex-shrink-0 mt-0.5" fill="currentColor" viewBox="0 0 20 20">
                      <path fillRule="evenodd" d="M16.403 12.652a3 3 0 0 0 0-5.304 3 3 0 0 0-3.75-3.751 3 3 0 0 0-5.305 0 3 3 0 0 0-3.751 3.75 3 3 0 0 0 0 5.305 3 3 0 0 0 3.75 3.751 3 3 0 0 0 5.305 0 3 3 0 0 0 3.751-3.75Zm-2.546-4.46a.75.75 0 0 0-1.214-.882l-3.483 4.79-1.88-1.88a.75.75 0 1 0-1.06 1.061l2.5 2.5a.75.75 0 0 0 1.137-.089l4-5.5Z" clipRule="evenodd" />
                    </svg>
                    <span>Enable secure communication between patients and doctors through encrypted channels</span>
                  </li>
                  <li className="flex gap-3">
                    <svg className="w-5 h-5 text-brand-teal flex-shrink-0 mt-0.5" fill="currentColor" viewBox="0 0 20 20">
                      <path fillRule="evenodd" d="M16.403 12.652a3 3 0 0 0 0-5.304 3 3 0 0 0-3.75-3.751 3 3 0 0 0-5.305 0 3 3 0 0 0-3.751 3.75 3 3 0 0 0 0 5.305 3 3 0 0 0 3.75 3.751 3 3 0 0 0 5.305 0 3 3 0 0 0 3.751-3.75Zm-2.546-4.46a.75.75 0 0 0-1.214-.882l-3.483 4.79-1.88-1.88a.75.75 0 1 0-1.06 1.061l2.5 2.5a.75.75 0 0 0 1.137-.089l4-5.5Z" clipRule="evenodd" />
                    </svg>
                    <span>Support digital prescriptions, medical records, and appointment management</span>
                  </li>
                  <li className="flex gap-3">
                    <svg className="w-5 h-5 text-brand-teal flex-shrink-0 mt-0.5" fill="currentColor" viewBox="0 0 20 20">
                      <path fillRule="evenodd" d="M16.403 12.652a3 3 0 0 0 0-5.304 3 3 0 0 0-3.75-3.751 3 3 0 0 0-5.305 0 3 3 0 0 0-3.751 3.75 3 3 0 0 0 0 5.305 3 3 0 0 0 3.75 3.751 3 3 0 0 0 5.305 0 3 3 0 0 0 3.751-3.75Zm-2.546-4.46a.75.75 0 0 0-1.214-.882l-3.483 4.79-1.88-1.88a.75.75 0 1 0-1.06 1.061l2.5 2.5a.75.75 0 0 0 1.137-.089l4-5.5Z" clipRule="evenodd" />
                    </svg>
                    <span>Ensure quality through doctor verification, patient ratings, and continuous monitoring</span>
                  </li>
                </ul>
              </section>

              <section>
                <h2 className="text-2xl font-bold text-brand-navy mt-8 mb-4">Our Vision</h2>
                <p className="leading-relaxed">
                  We envision a future where every person in East Africa has access to timely,
                  affordable, and professional healthcare services. eSiri is our contribution
                  toward building that future — one consultation at a time.
                </p>
              </section>

              <section>
                <h2 className="text-2xl font-bold text-brand-navy mt-8 mb-4">Healthcare Standards</h2>
                <p className="leading-relaxed">
                  All healthcare providers on our platform are required to hold valid medical licenses
                  and undergo a thorough verification process. We maintain strict professional standards
                  and continuously monitor the quality of care through patient feedback and regular audits.
                </p>
              </section>
            </div>
          </div>
        </div>
      )}
    </PageShell>
  );
}
