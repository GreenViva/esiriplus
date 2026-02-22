package com.esiri.esiriplus.feature.auth.screen

enum class DoctorLegalTab(val title: String) {
    PRIVACY_POLICY("Privacy Policy"),
    TERMS_OF_SERVICE("Terms of Service"),
    PRACTICE_GUIDELINES("Practice Guidelines"),
    PROVIDER_CONSENT("Provider Consent"),
}

object DoctorTermsContent {

    fun getSections(tab: DoctorLegalTab): List<LegalSection> = when (tab) {
        DoctorLegalTab.PRIVACY_POLICY -> privacyPolicy
        DoctorLegalTab.TERMS_OF_SERVICE -> termsOfService
        DoctorLegalTab.PRACTICE_GUIDELINES -> practiceGuidelines
        DoctorLegalTab.PROVIDER_CONSENT -> providerConsent
    }

    private val privacyPolicy = listOf(
        LegalSection(
            heading = "PRIVACY POLICY FOR HEALTHCARE PROVIDERS",
            body = "Last Updated: February 2026",
        ),
        LegalSection(
            heading = "Welcome, Healthcare Professionals",
            body = "This Privacy Policy explains how eSIRI Plus collects, uses, and protects your information as a registered healthcare provider on our platform. Unlike patients who remain anonymous, you must provide verifiable credentials to practice medicine on eSIRI Plus.",
        ),
        LegalSection(
            heading = "1. INFORMATION WE COLLECT FROM YOU",
            body = """Professional Information (Required for Verification)

Personal Details:
• Full legal name (as registered with Medical Council of Tanganyika)
• Email address
• Phone number
• Date of birth
• National ID or passport number (for identity verification)
• Current residential region in Tanzania

Professional Credentials:
• Medical license number (MCT registration)
• Specialty/field of practice
• Years of experience
• Professional qualifications and certifications
• Medical school and graduation year
• Languages you speak (Swahili, English, etc.)
• Current practicing facility (if applicable)

Verification Documents:
• Scanned copy of medical license
• Professional certificates
• Proof of identity (national ID or passport)
• Professional indemnity insurance (if applicable)

Financial Information:
• M-Pesa phone number for earnings payout
• Bank account details (if added for alternative payment)
• Tax identification number (TIN)
• Payment history and earnings records

Platform Activity Data:
• Consultation requests received and accepted/declined
• Response times and availability status
• Patient ratings and feedback
• Chat logs and consultation notes you create
• Video call participation records
• Reports you generate

Technical Data:
• Device information and IP address
• Login times and session duration
• App version and performance data""",
        ),
        LegalSection(
            heading = "2. HOW WE USE YOUR INFORMATION",
            body = """Verify Your Credentials:
• Confirm your identity with Medical Council of Tanganyika (MCT)
• Validate your medical license is current and in good standing
• Review your professional qualifications
• Conduct background checks as required by Tanzanian healthcare regulations

Facilitate Your Practice:
• Connect you with patients seeking care in your specialty
• Display your profile to verified patients (anonymously — they see specialty, ratings, availability)
• Process your consultation earnings (50% of each consultation fee)
• Track your performance metrics (acceptance rate, patient ratings, response time)

Ensure Quality Care:
• Monitor compliance with medical standards
• Review patient feedback and ratings
• Track consultation outcomes for quality assurance
• Investigate patient complaints if they arise

Legal Compliance:
• Maintain records as required by Tanzania's healthcare regulations
• Report to Medical Council of Tanganyika if required by law
• Respond to legal requests or court orders""",
        ),
        LegalSection(
            heading = "3. WHO CAN SEE YOUR INFORMATION",
            body = """Patients — Only your specialty, years of experience, average rating, availability status, and languages. Never your name or personal details.

eSIRI Plus HR Team — Full profile and credentials during verification and ongoing compliance monitoring.

eSIRI Plus Finance Team — Earnings data and payment information for processing monthly payouts.

Medical Council of Tanganyika — Your credentials for verification purposes; consultation records if legally required.

Tanzanian Authorities — Only if required by valid court order or healthcare regulations.

Other Doctors — Nothing. Your information is not shared with other providers.

We will never sell your personal or professional information to third parties.""",
        ),
        LegalSection(
            heading = "4. YOUR PROFESSIONAL PROFILE",
            body = """What Patients See About You:
• Specialty (e.g., "General Practitioner," "Psychologist")
• Years of experience
• Average patient rating (1–5 stars)
• Total consultations completed
• Languages you speak
• Availability status (available/unavailable)
• Consultation fee for your tier

What Patients Do NOT See:
• Your real name
• Your medical license number
• Your phone number or email
• Your physical location beyond "Tanzania"
• Any personal identifying information""",
        ),
        LegalSection(
            heading = "5. HOW WE PROTECT YOUR INFORMATION",
            body = """• Encrypted storage — All credentials stored with AES-256 encryption
• Secure document uploads — Professional certificates encrypted in transit and at rest
• Access controls — Only authorized HR and compliance staff can access your credentials
• Regular audits — Third-party security audits conducted annually
• Separate databases — Doctor credentials kept separate from patient data
• Multi-factor authentication — Required for all doctor accounts
• Activity logging — All access to your profile is logged for security""",
        ),
        LegalSection(
            heading = "6. HOW LONG WE KEEP YOUR INFORMATION",
            body = """• Professional credentials — As long as you're active on the platform, plus 7 years after account closure (legal requirement)
• Consultation records — 7 years from date of consultation (Tanzanian healthcare regulation)
• Earnings records — As required by Tanzanian financial regulations
• Performance metrics — Until account closure
• Verification documents — 10 years for regulatory compliance""",
        ),
        LegalSection(
            heading = "7. YOUR RIGHTS AS A HEALTHCARE PROVIDER",
            body = """You have the right to:

• Access your full profile and all data we hold about you
• Correct any inaccurate information in your profile
• Request deletion of your account (subject to legal retention requirements)
• Export your consultation history and earnings records
• Withdraw consent and stop practicing on the platform at any time
• Appeal verification decisions or account suspensions
• Lodge a complaint with TCRA or MCT if you believe your data rights are violated""",
        ),
        LegalSection(
            heading = "8. REPORTING OBLIGATIONS",
            body = """As a healthcare provider, you acknowledge that:

• eSIRI Plus may report credential violations to the Medical Council of Tanganyika
• Serious patient complaints may be investigated and reported to MCT
• Fraudulent credentials will result in immediate account termination and legal action
• Professional misconduct may be reported to relevant authorities""",
        ),
        LegalSection(
            heading = "9. CHANGES TO THIS POLICY",
            body = "We will notify you 30 days before making significant changes to this Privacy Policy via email and in-app notification.",
        ),
        LegalSection(
            heading = "10. CONTACT US",
            body = """For Privacy Questions:
• Email: provider-privacy@esiri.health
• Phone: +255 XXX XXX XXX
• In-app: Provider Portal → Help & Privacy

For Credential Verification:
• Email: verification@esiri.health""",
        ),
    )

    private val termsOfService = listOf(
        LegalSection(
            heading = "TERMS OF SERVICE FOR HEALTHCARE PROVIDERS",
            body = "Last Updated: February 2026",
        ),
        LegalSection(
            heading = "1. ELIGIBILITY TO PRACTICE ON eSIRI PLUS",
            body = """Professional Requirements:
• Hold a valid, current medical license issued by the Medical Council of Tanganyika (MCT)
• Be in good standing with MCT (no suspensions, restrictions, or disciplinary actions)
• Have professional indemnity insurance (recommended, may become required)
• Be physically located in Tanzania while providing consultations

Specialty Categories:
• Nurse (Muuguzi) — Registered Nurse license
• Clinical Officer (Afisa wa Kliniki) — Clinical Officer license
• Pharmacist (Mfamasia) — Pharmacist license
• General Practitioner (Daktari wa Jumla) — Medical Doctor license
• Specialist (Mtaalamu) — Medical Doctor license + specialty certification
• Psychologist (Mshauri wa Akili) — Licensed Clinical Psychologist

Age and Legal Capacity:
• Be at least 21 years old
• Have the legal capacity to enter into binding agreements""",
        ),
        LegalSection(
            heading = "2. THE VERIFICATION PROCESS",
            body = """Step 1: Registration
• Submit professional credentials via Provider Registration portal
• Upload scanned copies of medical license, certificates, and ID
• Provide M-Pesa number for earnings payout

Step 2: Verification (5–14 business days)
• HR team verifies credentials with MCT
• Reviews certificates and professional history
• May contact for additional documentation

Step 3: Approval
• Receive Provider Account access
• Set availability schedule
• Begin accepting consultation requests

Verification Fees:
• One-time verification fee: TZS 10,000 (non-refundable)
• Annual re-verification: TZS 5,000""",
        ),
        LegalSection(
            heading = "3. YOUR PROVIDER ACCOUNT",
            body = """Account Security:
• Uses email and secure password
• Enable biometric authentication
• Never share login credentials
• You are responsible for all activity under your account

Availability Management:
• Mark "Available" when ready to accept consultations
• Mark "Unavailable" when offline
• Set weekly availability schedule
• 5 minutes to accept incoming requests before they expire""",
        ),
        LegalSection(
            heading = "4. CONSULTATION FEES AND EARNINGS",
            body = """Fee Structure (Patient Pays → Your Earnings at 50%):
• Nurse — TZS 3,000 → TZS 1,500
• Clinical Officer — TZS 5,000 → TZS 2,500
• Pharmacist — TZS 5,000 → TZS 2,500
• General Practitioner — TZS 10,000 → TZS 5,000
• Specialist — TZS 30,000 → TZS 15,000
• Psychologist — TZS 50,000 → TZS 25,000

Additional Earnings:
• Session extension: 50% of extension fee
• Video call recharge: 50% of TZS 2,500 = TZS 1,250 per 3 minutes

Payout Terms:
• Earnings paid monthly via M-Pesa on the 5th business day of each month
• Minimum withdrawal: TZS 10,000
• New providers: First payout held 30 days (fraud prevention)
• Disputed consultations: earnings held pending resolution""",
        ),
        LegalSection(
            heading = "5. YOUR PROFESSIONAL RESPONSIBILITIES",
            body = """You must:
• Provide care that meets Tanzanian medical standards
• Practice only within your scope of expertise and licensure
• Refer patients to in-person care when telemedicine is inadequate
• Document all consultations clearly and completely
• Generate consultation reports within 24 hours
• Maintain patient confidentiality at all times

You must NOT:
• Prescribe controlled substances without proper assessment
• Provide care outside your specialty or competence
• Practice while under the influence of alcohol or drugs
• Guarantee specific medical outcomes
• Accept gifts or payments outside the eSIRI Plus platform from patients

Consultation Requirements:
• Accept/decline requests within 5 minutes
• Respond to messages within 2 minutes during active consultations
• Default text chat: 15 minutes (extendable)
• Default video call: 3 minutes (rechargeable)
• Submit consultation report within 24 hours""",
        ),
        LegalSection(
            heading = "6. PATIENT RATINGS AND FEEDBACK",
            body = """• Patients rate 1–5 stars after each consultation
• If rated below 5 stars, patient must provide written feedback
• Ratings appear on profile after 3+ consultations
• Maintain average rating of 4.0 stars or higher
• Providers below 4.0 for 3 consecutive months receive performance review
• Providers below 3.5 may face account suspension""",
        ),
        LegalSection(
            heading = "7. AVAILABILITY AND ACCEPTANCE RATE",
            body = """Target acceptance rate: 70% or higher.

Valid reasons to decline:
• Condition outside scope
• Patient requires in-person care
• Genuinely unavailable
• Ethical concerns""",
        ),
        LegalSection(
            heading = "8. PROHIBITED CONDUCT",
            body = """Professional Misconduct (NOT allowed):
• Practice with expired/suspended/revoked MCT license
• Misrepresent qualifications or experience
• Provide advice outside specialty
• Prescribe medications for conditions not assessed
• Share patient information outside the platform

Platform Abuse (NOT allowed):
• Request payment outside eSIRI Plus
• Share contact information with patients for off-platform consultations
• Create multiple provider accounts
• Manipulate ratings or reviews

Unethical Behavior (NOT allowed):
• Sexual misconduct or harassment of patients
• Discrimination based on age, sex, religion, ethnicity, HIV status, etc.
• Practicing while impaired

Violation Consequences:
• First offense: Warning + mandatory training
• Second offense: 30-day suspension
• Third offense or serious violation: Permanent ban + report to MCT""",
        ),
        LegalSection(
            heading = "9. MEDICAL LIABILITY",
            body = """You are solely responsible for all medical decisions and advice you provide. eSIRI Plus is a technology platform — not liable for medical outcomes. You should maintain professional indemnity insurance.

Indemnification: You agree to indemnify and hold eSIRI Plus harmless from any claims, damages, or legal costs arising from your medical practice on the platform.""",
        ),
        LegalSection(
            heading = "10. CONSULTATION DISPUTES",
            body = """Patient complaint process:
1. Patient submits complaint
2. eSIRI Plus reviews
3. You get 48 hours to respond
4. Determination made

Appeal within 14 days to provider-appeals@esiri.health.""",
        ),
        LegalSection(
            heading = "11. TERMINATION",
            body = """You may close your account at any time.

eSIRI Plus may suspend or terminate if:
• MCT license expires or is revoked
• Terms violated
• Rating below 3.5 for 3+ months
• Patient complaints indicate substandard care
• Fraudulent credentials
• Professional misconduct confirmed

Consultation records retained 7 years regardless of account closure.""",
        ),
        LegalSection(
            heading = "12. CHANGES TO THESE TERMS",
            body = "We will notify you 30 days before making significant changes via email and in-app notification.",
        ),
        LegalSection(
            heading = "13. DISPUTE RESOLUTION",
            body = """Governing law: Laws of the United Republic of Tanzania.

Dispute resolution process:
1. First: Contact provider support
2. If unresolved: Mediation
3. If mediation fails: Binding arbitration in Dar es Salaam""",
        ),
        LegalSection(
            heading = "14. CONTACT US",
            body = """• Provider Support: provider-support@esiri.health
• Verification: verification@esiri.health
• Billing/Earnings: provider-billing@esiri.health""",
        ),
    )

    private val practiceGuidelines = listOf(
        LegalSection(
            heading = "MEDICAL PRACTICE GUIDELINES FOR PROVIDERS",
            body = "Last Updated: February 2026",
        ),
        LegalSection(
            heading = "1. TELEMEDICINE SCOPE OF PRACTICE",
            body = """CAN Treat via eSIRI Plus:
• Minor acute illnesses (colds, flu, mild infections)
• Chronic condition follow-ups (diabetes, hypertension management)
• Medication refills for stable chronic conditions
• Mental health consultations (mild to moderate conditions)
• Dermatological conditions (with clear visual assessment)
• Sexual and reproductive health consultations
• Nutrition and lifestyle counseling
• Second opinions

MUST REFER to In-Person Care:
• Medical emergencies (chest pain, difficulty breathing, severe bleeding, etc.)
• Conditions requiring physical examination
• Suspected surgical conditions (appendicitis, ectopic pregnancy, etc.)
• Serious mental health crises (suicidal ideation, psychosis)
• Complicated pregnancies
• Conditions requiring lab tests, imaging, or procedures
• Pediatric emergencies or serious pediatric conditions""",
        ),
        LegalSection(
            heading = "2. INFORMED CONSENT AND PATIENT EDUCATION",
            body = """Before each consultation, ensure the patient understands:

• Telemedicine limitations
• May need in-person follow-up
• Should seek emergency care if symptoms worsen
• Importance of complete and honest information

Use the in-app consent acknowledgment feature.""",
        ),
        LegalSection(
            heading = "3. DOCUMENTATION REQUIREMENTS",
            body = """Every Consultation Must Include:
• Chief complaint (patient's reason for consultation)
• History of present illness
• Relevant medical history (chronic conditions, allergies, current medications)
• Assessment/diagnosis (or differential diagnoses)
• Treatment plan (medications, lifestyle changes, referrals)
• Patient education provided
• Follow-up plan

Reports:
• Use AI-assisted report tool
• Review and edit before submitting
• Submit within 24 hours""",
        ),
        LegalSection(
            heading = "4. PRESCRIBING GUIDELINES",
            body = """MAY Prescribe:
• Medications for conditions adequately assessed
• Refills for stable chronic conditions
• Antibiotics for clearly bacterial infections (use judiciously)
• Over-the-counter medications

MAY NOT Prescribe:
• Controlled substances (morphine, diazepam, tramadol, etc.)
• Medications for conditions not assessed
• High-risk medications requiring monitoring
• Medications outside your specialty""",
        ),
        LegalSection(
            heading = "5. PATIENT CONFIDENTIALITY",
            body = """Never:
• Discuss patient cases outside the platform
• Screenshot or share patient information
• Disclose consultations to anyone
• Attempt to identify anonymous patients

Permitted Disclosures:
• MCT if legally required
• Law enforcement with valid court order
• To protect patient safety from imminent harm""",
        ),
        LegalSection(
            heading = "6. CULTURAL SENSITIVITY",
            body = """• Respect patients using traditional medicine (ask about it, advise on interactions)
• Be sensitive to discussing sexual and reproductive health
• Explain medical terms in simple Swahili or English
• Avoid judgment about health-seeking behavior
• Understand resource limitations patients may face""",
        ),
        LegalSection(
            heading = "7. EMERGENCY SITUATIONS",
            body = """If a patient presents with an emergency:

1. Immediately advise: "This is an emergency. Call 112/114/115 or go to the nearest hospital NOW."
2. Do not continue the consultation
3. Document what you advised
4. Report to eSIRI Plus support immediately""",
        ),
        LegalSection(
            heading = "8. CHALLENGING SITUATIONS",
            body = """Inappropriate Medication Request:
• Explain why you cannot prescribe it
• Offer alternative treatment
• Document refusal

Incomplete Information:
• Explain why complete information is critical
• If patient refuses, document and consider declining consultation

Beyond Your Expertise:
• Be honest with the patient
• Refer appropriately
• Document referral""",
        ),
        LegalSection(
            heading = "9. CONTINUOUS PROFESSIONAL DEVELOPMENT",
            body = """Required:
• Maintain valid MCT license
• Complete MCT CME requirements
• Update eSIRI Plus with renewed credentials annually

Recommended:
• Attend eSIRI Plus provider training webinars
• Review telemedicine best practices
• Stay updated on Tanzanian healthcare regulations""",
        ),
        LegalSection(
            heading = "10. REPORTING OBLIGATIONS",
            body = """Must report to eSIRI Plus:
• Any change in MCT license status
• Any disciplinary action by MCT or other authorities
• Professional indemnity insurance changes
• Serious patient safety concerns""",
        ),
        LegalSection(
            heading = "11. CONTACT",
            body = """• Medical Practice Questions: clinical-affairs@esiri.health
• Ethical Dilemmas: ethics@esiri.health
• Technical Issues: In-app: Provider Portal → Report Issue""",
        ),
    )

    private val providerConsent = listOf(
        LegalSection(
            heading = "PROVIDER INFORMED CONSENT",
            body = "Last Updated: February 2026\n\nBy registering and practicing on eSIRI Plus, you acknowledge and agree to the following:",
        ),
        LegalSection(
            heading = "1. INDEPENDENT CONTRACTOR STATUS",
            body = """• I am an independent contractor, not an employee of eSIRI Plus
• eSIRI Plus does not provide benefits (health insurance, paid leave, etc.)
• I am responsible for my own taxes and regulatory compliance
• The patient-provider relationship is between me and the patient, not eSIRI Plus""",
        ),
        LegalSection(
            heading = "2. MEDICAL LIABILITY",
            body = """• I am solely responsible for all medical decisions and advice I provide
• eSIRI Plus is not liable for my medical practice or patient outcomes
• I should maintain professional indemnity insurance
• Patient complaints may be reported to the Medical Council of Tanganyika""",
        ),
        LegalSection(
            heading = "3. VERIFICATION AND COMPLIANCE",
            body = """• All credentials I submitted are genuine and current
• My MCT license is valid and in good standing
• I will immediately notify eSIRI Plus if my license status changes
• I have professional indemnity insurance (or understand the risks of practicing without it)""",
        ),
        LegalSection(
            heading = "4. PLATFORM RULES",
            body = """I agree to:

• Provide care meeting Tanzanian medical standards
• Practice only within my scope
• Maintain patient confidentiality
• Respond to consultations promptly
• Submit reports within 24 hours
• Accept eSIRI Plus's 50/50 revenue split""",
        ),
        LegalSection(
            heading = "5. EARNINGS AND TAXES",
            body = """• eSIRI Plus pays me 50% of each consultation fee
• I am responsible for declaring this income and paying applicable taxes
• Payouts are monthly via M-Pesa
• Minimum payout is TZS 10,000""",
        ),
        LegalSection(
            heading = "6. TERMINATION",
            body = """• eSIRI Plus may terminate my account if I violate Terms, provide substandard care, or my license expires
• I may close my account at any time
• Consultation records are retained for 7 years regardless of account closure""",
        ),
        LegalSection(
            heading = "7. DATA CONSENT",
            body = """I consent to:

• eSIRI Plus verifying my credentials with MCT
• My specialty, ratings, and availability being displayed to patients (without revealing my name)
• Consultation records stored for 7 years
• Patient feedback visible on my profile
• Performance metrics being tracked""",
        ),
        LegalSection(
            heading = "8. LEGAL ACKNOWLEDGMENT",
            body = """• I have read and understood all Provider Terms of Service
• I have read and understood the Medical Practice Guidelines
• I voluntarily consent to practice on eSIRI Plus under these terms
• I am at least 21 years old with a valid medical license
• I understand my professional responsibilities and liability
• I will maintain the highest standards of medical ethics and professionalism

Date Accepted: Auto-recorded upon registration
Provider ID: Assigned after verification
Medical License Number: Your MCT registration number
Digital Signature: Confirmed by completing registration""",
        ),
        LegalSection(
            heading = "CONTACT",
            body = """Questions about this consent?
• Email: provider-legal@esiri.health
• Phone: +255 XXX XXX XXX
• Medical Council of Tanganyika: +255 22 XXX XXXX""",
        ),
    )
}
