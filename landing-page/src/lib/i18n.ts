export type Locale = "en" | "sw";

const translations = {
  en: {
    // Nav
    "nav.home": "Home",
    "nav.about": "About",
    "nav.features": "Features",
    "nav.doctors": "For Doctors",
    "nav.support": "Support",
    "nav.download": "Download App",

    // Hero
    "hero.title": "Healthcare Consultation, Anywhere in Tanzania.",
    "hero.subtitle":
      "Connect with verified doctors through secure messaging, voice, or video consultations — from the comfort of your home.",
    "hero.cta.download": "Download App",
    "hero.cta.learn": "Learn More",

    // About
    "about.tag": "Our Mission",
    "about.title": "Bridging the Gap in Healthcare Access",
    "about.description":
      "eSiri is a digital health platform built to make quality healthcare accessible to every Tanzanian. We connect patients with verified, licensed doctors for real-time consultations — eliminating distance, wait times, and barriers to care.",
    "about.card1.title": "Accessible Healthcare",
    "about.card1.text":
      "Quality medical consultations available to anyone with a smartphone, regardless of location.",
    "about.card2.title": "Verified Doctors",
    "about.card2.text":
      "Every doctor on eSiri is licensed and verified, ensuring you receive professional medical advice.",
    "about.card3.title": "Digital Innovation",
    "about.card3.text":
      "Leveraging technology to modernize healthcare delivery across Tanzania and East Africa.",

    // Features
    "features.tag": "Platform Features",
    "features.title": "Everything You Need for Remote Healthcare",
    "features.subtitle":
      "eSiri provides a complete telemedicine experience for patients and doctors alike.",
    "features.consultation.title": "Instant Doctor Consultation",
    "features.consultation.text":
      "Request a consultation and connect with a qualified doctor within minutes.",
    "features.messaging.title": "Secure Messaging",
    "features.messaging.text":
      "Communicate safely with your doctor through end-to-end encrypted chat.",
    "features.video.title": "Audio & Video Calls",
    "features.video.text":
      "Face-to-face remote consultations with crystal-clear audio and video quality.",
    "features.scheduling.title": "Appointment Scheduling",
    "features.scheduling.text":
      "Book consultations at a time that works for you with flexible scheduling.",
    "features.ratings.title": "Doctor Ratings",
    "features.ratings.text":
      "Rate your consultations to help maintain high-quality healthcare standards.",
    "features.records.title": "Medical Records",
    "features.records.text":
      "Your consultation history and prescriptions, securely stored and always accessible.",

    // Platform — for both patients and doctors
    "platform.tag": "Built for Everyone",
    "platform.title": "A Platform for Patients and Doctors",
    "platform.subtitle":
      "eSiri serves both sides of healthcare — giving patients anonymous, stigma-free access to care, and giving doctors a modern way to grow their practice.",
    "platform.patients.heading": "For Patients",
    "platform.patient1.title": "100% Anonymous Consultations",
    "platform.patient1.text":
      "Seek medical advice without revealing your identity. No name, no photo — just the care you need, free from stigma or judgment.",
    "platform.patient2.title": "Instant Access, Anytime",
    "platform.patient2.text":
      "Connect with a verified doctor in minutes — no travel, no waiting rooms, no embarrassment.",
    "platform.patient3.title": "Your Data, Your Control",
    "platform.patient3.text":
      "End-to-end encrypted conversations. You decide what to share and can delete your records at any time.",
    "platform.doctors.heading": "For Doctors",
    "platform.doctor1.title": "Expand Your Patient Reach",
    "platform.doctor1.text":
      "Connect with patients across Tanzania, beyond the walls of your clinic. Set your own availability.",
    "platform.doctor2.title": "Build Your Reputation",
    "platform.doctor2.text":
      "Earn ratings and reviews from satisfied patients to grow your digital practice.",
    "platform.doctor3.title": "Earn on Your Terms",
    "platform.doctor3.text":
      "Flexible scheduling, transparent earnings, and professional-grade tools for consultations and prescriptions.",

    // App Download
    "download.tag": "Get Started",
    "download.title": "Download eSiri Today",
    "download.subtitle":
      "Available on Android. Start your first consultation in minutes.",
    "download.playstore": "Get it on Google Play",
    "download.coming": "iOS Coming Soon",
    "download.scan": "Scan to download",

    // Trust
    "trust.tag": "Why Trust eSiri",
    "trust.title": "Your Health, Our Priority",
    "trust.subtitle":
      "We take every measure to ensure your healthcare experience is safe, private, and professional.",
    "trust.verification.title": "Doctor Verification",
    "trust.verification.text":
      "Every doctor undergoes license verification and credential checks before joining the platform.",
    "trust.encryption.title": "End-to-End Encryption",
    "trust.encryption.text":
      "All communications between patients and doctors are encrypted and secure.",
    "trust.privacy.title": "Data Privacy",
    "trust.privacy.text":
      "Your medical data is protected with industry-standard security measures and never shared without consent.",
    "trust.standards.title": "Professional Standards",
    "trust.standards.text":
      "We maintain strict quality standards with regular audits and patient feedback systems.",

    // Stats
    "stats.doctors": "Verified Doctors",
    "stats.consultations": "Consultations",
    "stats.patients": "Active Patients",
    "stats.rating": "Average Rating",

    // Contact
    "contact.tag": "Get in Touch",
    "contact.title": "Contact & Support",
    "contact.subtitle":
      "Have questions? We're here to help. Reach out through any of these channels.",
    "contact.email": "Email Us",
    "contact.email.address": "support@esiri.africa",
    "contact.phone": "Call Us",
    "contact.phone.number": "+255 XXX XXX XXX",
    "contact.hours": "Business Hours",
    "contact.hours.text": "Mon - Fri, 8:00 AM - 6:00 PM EAT",
    "contact.form.name": "Your Name",
    "contact.form.email": "Your Email",
    "contact.form.message": "Your Message",
    "contact.form.submit": "Send Message",
    "contact.form.success": "Message sent! We will respond within 24 hours.",

    // FAQ
    "faq.title": "Frequently Asked Questions",
    "faq.q1": "How do I start a consultation?",
    "faq.a1":
      "Download the eSiri app, create an account, and browse available doctors. Select a doctor, choose your consultation type (chat, audio, or video), and you're connected.",
    "faq.q2": "Are the doctors verified?",
    "faq.a2":
      "Yes. Every doctor on eSiri undergoes a thorough verification process including license validation, credential checks, and identity verification before they can accept consultations.",
    "faq.q3": "How much does a consultation cost?",
    "faq.a3":
      "Consultation fees vary by doctor and service tier. You can see the exact cost before booking. We offer affordable rates to make healthcare accessible to everyone.",
    "faq.q4": "Is my data secure?",
    "faq.a4":
      "Absolutely. All communications are encrypted end-to-end. Your medical records are stored securely and are only accessible to you and your consulting doctor.",
    "faq.q5": "Can I get prescriptions through eSiri?",
    "faq.a5":
      "Yes. Doctors can issue digital prescriptions during your consultation, which you can take to any pharmacy.",
    "faq.q6": "What if I need emergency care?",
    "faq.a6":
      "eSiri is designed for non-emergency consultations. If you have a medical emergency, please call emergency services or visit your nearest hospital immediately.",

    // Footer
    "footer.description":
      "Connecting patients with verified doctors for accessible, secure, and professional healthcare consultations across Tanzania.",
    "footer.product": "Product",
    "footer.company": "Company",
    "footer.legal": "Legal",
    "footer.about": "About eSiri",
    "footer.features": "Features",
    "footer.pricing": "Pricing",
    "footer.download": "Download",
    "footer.mission": "Our Mission",
    "footer.careers": "Careers",
    "footer.contact": "Contact",
    "footer.blog": "Blog",
    "footer.privacy": "Privacy Policy",
    "footer.terms": "Terms of Service",
    "footer.data": "Data Protection",
    "footer.cookies": "Cookie Policy",
    "footer.rights": "All rights reserved.",
    "footer.newsletter": "Stay Updated",
    "footer.newsletter.text": "Get the latest health tips and platform updates.",
    "footer.newsletter.placeholder": "Enter your email",
    "footer.newsletter.submit": "Subscribe",

    // Pages
    "privacy.title": "Privacy Policy",
    "privacy.updated": "Last updated",
    "terms.title": "Terms of Service",
    "terms.updated": "Last updated",
    "about.page.title": "About eSiri",
    "support.page.title": "Support Center",

    // Doctor Apply
    "apply.title": "Apply to Join eSiri",
    "apply.subtitle":
      "Complete this form to begin your onboarding as a healthcare provider on eSiri.",
    "apply.fullname": "Full Name",
    "apply.email": "Email Address",
    "apply.phone": "Phone Number",
    "apply.license": "Medical License Number",
    "apply.specialty": "Specialty",
    "apply.experience": "Years of Experience",
    "apply.bio": "Brief Bio",
    "apply.submit": "Submit Application",
    "apply.success":
      "Application submitted! Our team will review your credentials and contact you within 3-5 business days.",
    "apply.specialties.general": "General Practice",
    "apply.specialties.pediatrics": "Pediatrics",
    "apply.specialties.obstetrics": "Obstetrics & Gynecology",
    "apply.specialties.internal": "Internal Medicine",
    "apply.specialties.dermatology": "Dermatology",
    "apply.specialties.psychiatry": "Psychiatry",
    "apply.specialties.cardiology": "Cardiology",
    "apply.specialties.other": "Other",
  },

  sw: {
    // Nav
    "nav.home": "Nyumbani",
    "nav.about": "Kuhusu",
    "nav.features": "Huduma",
    "nav.doctors": "Kwa Madaktari",
    "nav.support": "Msaada",
    "nav.download": "Pakua App",

    // Hero
    "hero.title": "Huduma za Afya, Popote Tanzania.",
    "hero.subtitle":
      "Ungana na madaktari waliothibitishwa kupitia ujumbe, simu, au video — kutoka nyumbani kwako.",
    "hero.cta.download": "Pakua App",
    "hero.cta.learn": "Jifunze Zaidi",

    // About
    "about.tag": "Dhamira Yetu",
    "about.title": "Kuunganisha Pengo la Huduma za Afya",
    "about.description":
      "eSiri ni jukwaa la afya ya dijitali lililotengenezwa kufanya huduma bora za afya kupatikana kwa kila Mtanzania. Tunaunganisha wagonjwa na madaktari waliothibitishwa kwa mashauriano ya wakati halisi.",
    "about.card1.title": "Huduma za Afya Zinazofikiwa",
    "about.card1.text":
      "Mashauriano ya kimatibabu yanapatikana kwa mtu yeyote mwenye simu mahiri.",
    "about.card2.title": "Madaktari Waliothibitishwa",
    "about.card2.text":
      "Kila daktari kwenye eSiri ana leseni na amethibitishwa.",
    "about.card3.title": "Uvumbuzi wa Dijitali",
    "about.card3.text":
      "Kutumia teknolojia kuboresha utoaji wa huduma za afya Tanzania.",

    // Features
    "features.tag": "Huduma za Jukwaa",
    "features.title": "Kila Unachohitaji kwa Huduma za Afya",
    "features.subtitle":
      "eSiri inatoa uzoefu kamili wa telemedicine kwa wagonjwa na madaktari.",
    "features.consultation.title": "Mashauriano ya Haraka",
    "features.consultation.text":
      "Omba mashauriano na uunganishwe na daktari ndani ya dakika.",
    "features.messaging.title": "Ujumbe Salama",
    "features.messaging.text":
      "Wasiliana kwa usalama na daktari wako kupitia mazungumzo yaliyosimbwa.",
    "features.video.title": "Simu za Sauti na Video",
    "features.video.text":
      "Mashauriano ya ana kwa ana ya mbali na ubora wa sauti na video.",
    "features.scheduling.title": "Kupanga Miadi",
    "features.scheduling.text":
      "Panga mashauriano wakati unaokufaa na ratiba inayobadilika.",
    "features.ratings.title": "Ukadiriaji wa Madaktari",
    "features.ratings.text":
      "Kadiria mashauriano yako kusaidia kudumisha viwango vya juu.",
    "features.records.title": "Rekodi za Kimatibabu",
    "features.records.text":
      "Historia yako ya mashauriano na maagizo, yamehifadhiwa kwa usalama.",

    // Platform — for both patients and doctors
    "platform.tag": "Imejengwa kwa Wote",
    "platform.title": "Jukwaa kwa Wagonjwa na Madaktari",
    "platform.subtitle":
      "eSiri inahudumia pande zote za afya — kuwapa wagonjwa ufikiaji wa siri, bila aibu, na kuwapa madaktari njia ya kisasa ya kukuza mazoezi yao.",
    "platform.patients.heading": "Kwa Wagonjwa",
    "platform.patient1.title": "Mashauriano ya Siri 100%",
    "platform.patient1.text":
      "Tafuta ushauri wa kimatibabu bila kufichua utambulisho wako. Hakuna jina, hakuna picha — huduma unayohitaji tu, bila aibu.",
    "platform.patient2.title": "Ufikiaji wa Haraka, Wakati Wowote",
    "platform.patient2.text":
      "Ungana na daktari aliyethibitishwa ndani ya dakika — hakuna kusafiri, hakuna kusubiri.",
    "platform.patient3.title": "Data Yako, Udhibiti Wako",
    "platform.patient3.text":
      "Mazungumzo yaliyosimbwa mwisho-hadi-mwisho. Unaamua unachoshiriki na unaweza kufuta rekodi zako wakati wowote.",
    "platform.doctors.heading": "Kwa Madaktari",
    "platform.doctor1.title": "Panua Ufikiaji wa Wagonjwa",
    "platform.doctor1.text":
      "Unganisha na wagonjwa kote Tanzania, zaidi ya kuta za kliniki yako. Weka ratiba yako mwenyewe.",
    "platform.doctor2.title": "Jenga Sifa Yako",
    "platform.doctor2.text":
      "Pata ukadiriaji na mapitio kutoka kwa wagonjwa walioridhika kukuza mazoezi yako ya dijitali.",
    "platform.doctor3.title": "Pata Kipato kwa Masharti Yako",
    "platform.doctor3.text":
      "Ratiba inayobadilika, mapato wazi, na zana za kitaalamu za mashauriano na maagizo.",

    // App Download
    "download.tag": "Anza",
    "download.title": "Pakua eSiri Leo",
    "download.subtitle":
      "Inapatikana kwenye Android. Anza mashauriano yako ya kwanza ndani ya dakika.",
    "download.playstore": "Pata kwenye Google Play",
    "download.coming": "iOS Inakuja Hivi Karibuni",
    "download.scan": "Scan kupakua",

    // Trust
    "trust.tag": "Kwa Nini Uamini eSiri",
    "trust.title": "Afya Yako, Kipaumbele Chetu",
    "trust.subtitle":
      "Tunachukua hatua zote kuhakikisha uzoefu wako wa afya ni salama na wa kitaalamu.",
    "trust.verification.title": "Uthibitishaji wa Madaktari",
    "trust.verification.text":
      "Kila daktari anapitia uthibitishaji wa leseni kabla ya kujiunga.",
    "trust.encryption.title": "Usimbaji wa Mwisho-hadi-Mwisho",
    "trust.encryption.text":
      "Mawasiliano yote kati ya wagonjwa na madaktari yamesimbwa na salama.",
    "trust.privacy.title": "Faragha ya Data",
    "trust.privacy.text":
      "Data yako ya kimatibabu inalindwa na haishirikiwi bila idhini.",
    "trust.standards.title": "Viwango vya Kitaalamu",
    "trust.standards.text":
      "Tunadumisha viwango vikali vya ubora na ukaguzi wa mara kwa mara.",

    // Stats
    "stats.doctors": "Madaktari Waliothibitishwa",
    "stats.consultations": "Mashauriano",
    "stats.patients": "Wagonjwa Hai",
    "stats.rating": "Ukadiriaji wa Wastani",

    // Contact
    "contact.tag": "Wasiliana Nasi",
    "contact.title": "Mawasiliano na Msaada",
    "contact.subtitle":
      "Una maswali? Tuko hapa kusaidia. Wasiliana nasi kupitia njia yoyote hizi.",
    "contact.email": "Tuma Barua Pepe",
    "contact.email.address": "support@esiri.africa",
    "contact.phone": "Piga Simu",
    "contact.phone.number": "+255 XXX XXX XXX",
    "contact.hours": "Saa za Kazi",
    "contact.hours.text": "Jumatatu - Ijumaa, 8:00 AM - 6:00 PM EAT",
    "contact.form.name": "Jina Lako",
    "contact.form.email": "Barua Pepe Yako",
    "contact.form.message": "Ujumbe Wako",
    "contact.form.submit": "Tuma Ujumbe",
    "contact.form.success":
      "Ujumbe umetumwa! Tutajibu ndani ya masaa 24.",

    // FAQ
    "faq.title": "Maswali Yanayoulizwa Mara kwa Mara",
    "faq.q1": "Ninawezaje kuanza mashauriano?",
    "faq.a1":
      "Pakua app ya eSiri, fungua akaunti, na tazama madaktari wanaopatikana. Chagua daktari, chagua aina ya mashauriano, na uunganishwe.",
    "faq.q2": "Je, madaktari wamethibitishwa?",
    "faq.a2":
      "Ndiyo. Kila daktari kwenye eSiri anapitia utaratibu wa kina wa uthibitishaji ikiwa ni pamoja na uthibitishaji wa leseni.",
    "faq.q3": "Mashauriano yanagharimu kiasi gani?",
    "faq.a3":
      "Ada za mashauriano zinatofautiana kwa daktari na kiwango cha huduma. Unaweza kuona gharama halisi kabla ya kuhifadhi.",
    "faq.q4": "Je, data yangu ni salama?",
    "faq.a4":
      "Kabisa. Mawasiliano yote yamesimbwa mwisho-hadi-mwisho. Rekodi zako za kimatibabu zimehifadhiwa kwa usalama.",
    "faq.q5": "Naweza kupata maagizo kupitia eSiri?",
    "faq.a5":
      "Ndiyo. Madaktari wanaweza kutoa maagizo ya dijitali wakati wa mashauriano yako.",
    "faq.q6": "Nikifanya nini kama nahitaji huduma ya dharura?",
    "faq.a6":
      "eSiri imetengenezwa kwa mashauriano yasiyo ya dharura. Kama una dharura ya kimatibabu, tafadhali piga simu huduma za dharura.",

    // Footer
    "footer.description":
      "Kuunganisha wagonjwa na madaktari waliothibitishwa kwa huduma za afya zinazofikiwa, salama, na za kitaalamu kote Tanzania.",
    "footer.product": "Bidhaa",
    "footer.company": "Kampuni",
    "footer.legal": "Kisheria",
    "footer.about": "Kuhusu eSiri",
    "footer.features": "Huduma",
    "footer.pricing": "Bei",
    "footer.download": "Pakua",
    "footer.mission": "Dhamira Yetu",
    "footer.careers": "Kazi",
    "footer.contact": "Mawasiliano",
    "footer.blog": "Blogu",
    "footer.privacy": "Sera ya Faragha",
    "footer.terms": "Masharti ya Huduma",
    "footer.data": "Ulinzi wa Data",
    "footer.cookies": "Sera ya Kuki",
    "footer.rights": "Haki zote zimehifadhiwa.",
    "footer.newsletter": "Endelea Kupata Habari",
    "footer.newsletter.text":
      "Pata vidokezo vya afya na masasisho ya jukwaa.",
    "footer.newsletter.placeholder": "Weka barua pepe yako",
    "footer.newsletter.submit": "Jiandikishe",

    // Pages
    "privacy.title": "Sera ya Faragha",
    "privacy.updated": "Ilisasishwa mwisho",
    "terms.title": "Masharti ya Huduma",
    "terms.updated": "Ilisasishwa mwisho",
    "about.page.title": "Kuhusu eSiri",
    "support.page.title": "Kituo cha Msaada",

    // Doctor Apply
    "apply.title": "Omba Kujiunga na eSiri",
    "apply.subtitle":
      "Jaza fomu hii kuanza mchakato wako wa kujiunga kama mtoa huduma za afya kwenye eSiri.",
    "apply.fullname": "Jina Kamili",
    "apply.email": "Anwani ya Barua Pepe",
    "apply.phone": "Nambari ya Simu",
    "apply.license": "Nambari ya Leseni ya Kimatibabu",
    "apply.specialty": "Utaalamu",
    "apply.experience": "Miaka ya Uzoefu",
    "apply.bio": "Wasifu Mfupi",
    "apply.submit": "Tuma Maombi",
    "apply.success":
      "Maombi yametumwa! Timu yetu itakagua stakabadhi zako na kukuwasiliana ndani ya siku 3-5 za kazi.",
    "apply.specialties.general": "Daktari Mkuu",
    "apply.specialties.pediatrics": "Watoto",
    "apply.specialties.obstetrics": "Uzazi na Wanawake",
    "apply.specialties.internal": "Dawa ya Ndani",
    "apply.specialties.dermatology": "Ngozi",
    "apply.specialties.psychiatry": "Akili",
    "apply.specialties.cardiology": "Moyo",
    "apply.specialties.other": "Nyingine",
  },
} as const;

type TranslationKey = keyof (typeof translations)["en"];

export function t(key: TranslationKey, locale: Locale = "en"): string {
  return translations[locale]?.[key] ?? translations.en[key] ?? key;
}

export function getTranslations(locale: Locale) {
  return (key: TranslationKey) => t(key, locale);
}
