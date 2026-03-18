# eSIRI Plus - iOS Development Reference
## Complete Feature & API Reference from Android Project

> **Purpose**: This document serves as the single source of truth for building the iOS version of eSIRI Plus.
> It documents every feature, screen, API call, data model, business logic rule, and integration point
> extracted from the Android codebase.

---

## Table of Contents

1. [App Overview & Architecture](#1-app-overview--architecture)
2. [Module Structure](#2-module-structure)
3. [Data Models (Domain Layer)](#3-data-models-domain-layer)
4. [Repository Interfaces & Methods](#4-repository-interfaces--methods)
5. [Use Cases (Business Logic)](#5-use-cases-business-logic)
6. [Authentication Feature](#6-authentication-feature)
7. [Patient Feature](#7-patient-feature)
8. [Doctor Feature](#8-doctor-feature)
9. [Chat & Video Call Feature](#9-chat--video-call-feature)
10. [Network Layer](#10-network-layer)
11. [Database Layer](#11-database-layer)
12. [Supabase Edge Functions (Backend API)](#12-supabase-edge-functions-backend-api)
13. [Push Notifications (FCM)](#13-push-notifications-fcm)
14. [Background Workers](#14-background-workers)
15. [Admin Panel Integration Points](#15-admin-panel-integration-points)
16. [Security Architecture](#16-security-architecture)
17. [UI/UX Design System](#17-uiux-design-system)
18. [Localization](#18-localization)
19. [Third-Party Integrations](#19-third-party-integrations)
20. [Environment Variables & Configuration](#20-environment-variables--configuration)
21. [iOS-Specific Recommendations](#21-ios-specific-recommendations)

---

## 1. App Overview & Architecture

### What is eSIRI Plus?
A telemedicine platform connecting patients (anonymous, no account needed) with verified doctors for:
- Real-time chat consultations
- Video/audio calls
- Appointment scheduling
- M-Pesa mobile payments (Tanzania-focused)
- Medical reports and prescriptions
- Doctor ratings and reviews

### Architecture Pattern
- **Clean Architecture**: UI → Domain → Data
- **MVVM**: ViewModels with reactive state (StateFlow → SwiftUI @Published equivalent)
- **Repository Pattern**: Domain interfaces, Network/Database implementations
- **Dependency Injection**: Hilt on Android → use Swift DI container or Swinject for iOS
- **Multi-Module**: Feature isolation (auth, patient, doctor, chat)

### Two User Types
1. **Patient**: Anonymous user with auto-generated ID (ESR-XXXX-XXXX). No email/password. Custom JWT tokens.
2. **Doctor**: Email/password via Supabase Auth. Must be verified by admin before accepting consultations.

---

## 2. Module Structure

```
Recommended iOS Module Mapping:

Android Module          →  iOS Equivalent
─────────────────────────────────────────
app/                    →  App/ (main target, entry point)
core/common/            →  Core/Common/ (Result, errors, utils, validation)
core/domain/            →  Core/Domain/ (models, repository protocols, use cases)
core/network/           →  Core/Network/ (API client, DTOs, interceptors, Supabase)
core/database/          →  Core/Database/ (CoreData/SwiftData or Realm)
core/ui/                →  Core/UI/ (shared SwiftUI components)
feature/auth/           →  Features/Auth/
feature/patient/        →  Features/Patient/
feature/doctor/         →  Features/Doctor/
feature/chat/           →  Features/Chat/
build-logic/            →  (not needed - use SPM or Xcode project config)
```

### Module Dependencies
```
App
├── Core/Common
├── Core/Domain
├── Core/Network
├── Core/Database
├── Features/Auth
├── Features/Patient
├── Features/Doctor
└── Features/Chat

Features/Auth → Core/Domain, Core/Common, Core/Network, Core/Database, Core/UI
Features/Patient → Core/Domain, Core/Common, Core/UI, Core/Network, Core/Database, Features/Chat
Features/Doctor → Core/Domain, Core/Common, Core/UI, Core/Network, Core/Database, Features/Chat
Features/Chat → Core/Domain, Core/Network
Core/Network → Core/Common, Core/Domain, Core/Database
Core/Database → Core/Common
Core/Domain → Core/Common
```

---

## 3. Data Models (Domain Layer)

### 3.1 User & Authentication Models

```
User
├── id: String                    // Unique user identifier
├── fullName: String              // User's full name
├── phone: String                 // Contact phone number
├── email: String?                // Email (nullable)
├── role: UserRole                // PATIENT or DOCTOR
└── isVerified: Boolean           // Verification status (default false)

UserRole (enum)
├── PATIENT
└── DOCTOR

Session
├── accessToken: String           // JWT access token
├── refreshToken: String          // JWT refresh token
├── expiresAt: Instant            // Expiration timestamp
├── user: User                    // Associated user
├── createdAt: Instant            // Creation timestamp
├── isExpired: Boolean            // Computed: current time > expiresAt
└── isRefreshWindowExpired: Bool  // Computed: 7 days since creation
    Constants: REFRESH_WINDOW_DAYS = 7

AuthState (sealed/enum)
├── Loading                       // Session loading
├── Authenticated(session)        // Valid session
├── Unauthenticated              // No session
└── SessionExpired               // Needs refresh
```

### 3.2 Consultation Models

```
Consultation
├── id: String
├── patientId: String
├── doctorId: String?             // Nullable (not assigned yet)
├── serviceType: ServiceType
├── status: ConsultationStatus    // Default: PENDING
├── notes: String?
├── createdAt: Instant
└── updatedAt: Instant?

ConsultationStatus (enum)
├── PENDING                       // Awaiting doctor
├── ASSIGNED                      // Doctor assigned
├── ACTIVE                        // Ongoing
├── IN_PROGRESS                   // Same as ACTIVE
├── AWAITING_EXTENSION            // Extension requested
├── GRACE_PERIOD                  // Payment window for extension
├── COMPLETED                     // Finished
└── CANCELLED                     // Cancelled

ConsultationRequest
├── requestId: String
├── patientSessionId: String
├── doctorId: String
├── serviceType: String
├── status: ConsultationRequestStatus
├── createdAt: Long (epoch ms)
├── expiresAt: Long (epoch ms)    // 60 seconds TTL
└── consultationId: String?       // Set after ACCEPTED

ConsultationRequestStatus (enum)
├── PENDING
├── ACCEPTED
├── REJECTED
├── EXPIRED
└── UNKNOWN

ConsultationSessionState
├── consultationId: String
├── phase: ConsultationPhase      // ACTIVE, AWAITING_EXTENSION, GRACE_PERIOD, COMPLETED
├── remainingSeconds: Int
├── totalDurationMinutes: Int     // Default 15
├── originalDurationMinutes: Int  // Default 15
├── extensionCount: Int           // Default 0
├── serviceType: String
├── consultationFee: Int
├── scheduledEndAtEpochMs: Long
├── gracePeriodEndAtEpochMs: Long
├── isLoading: Boolean
├── error: String?
├── extensionRequested: Boolean
└── patientDeclined: Boolean

ServiceType (enum)
├── GENERAL_CONSULTATION
├── SPECIALIST_CONSULTATION
├── FOLLOW_UP
└── EMERGENCY
```

### 3.3 Appointment Models

```
Appointment
├── appointmentId: String
├── doctorId: String
├── patientSessionId: String
├── scheduledAt: Long (epoch ms)
├── durationMinutes: Int          // Default 15
├── status: AppointmentStatus     // Default: BOOKED
├── serviceType: String
├── consultationType: String      // Default "chat"
├── chiefComplaint: String
├── consultationFee: Int
├── consultationId: String?       // Set when session starts
├── rescheduledFrom: String?      // Original appointment if rescheduled
├── createdAt: Long
└── updatedAt: Long

AppointmentStatus (enum)
├── BOOKED
├── CONFIRMED
├── IN_PROGRESS
├── COMPLETED
├── MISSED
├── CANCELLED
└── RESCHEDULED

DoctorAvailabilitySlot
├── slotId: String
├── doctorId: String
├── dayOfWeek: Int                // 0=Sunday, 6=Saturday
├── startTime: String             // HH:mm format
├── endTime: String               // HH:mm format
├── bufferMinutes: Int            // Default 5
├── isActive: Boolean             // Default true
├── createdAt: Long
└── updatedAt: Long
```

### 3.4 Doctor Models

```
DoctorProfile
├── doctorId: String
├── fullName: String
├── email: String
├── phone: String
├── specialty: ServiceType
├── languages: [String]           // e.g. ["en", "sw"]
├── bio: String
├── licenseNumber: String
├── yearsExperience: Int
├── profilePhotoUrl: String?
├── averageRating: Double         // Default 0.0
├── totalRatings: Int             // Default 0
├── isVerified: Boolean           // Default false
├── isAvailable: Boolean          // Default false
├── createdAt: Long
└── updatedAt: Long

DoctorRegistration
├── email: String
├── password: String
├── fullName: String
├── countryCode: String           // e.g. "+254"
├── phone: String
├── specialty: String
├── customSpecialty: String
├── country: String
├── languages: [String]
├── licenseNumber: String
├── yearsExperience: Int
├── bio: String
├── services: [String]
├── profilePhotoUri: String?
├── licenseDocumentUri: String?
└── certificatesUri: String?

DoctorCredentials
├── credentialId: String
├── doctorId: String
├── documentUrl: String
├── documentType: CredentialType  // LICENSE, CERTIFICATE, ID
└── verifiedAt: Long?

DoctorStatus (enum)
├── PENDING
├── ACTIVE
├── SUSPENDED
├── REJECTED
└── BANNED

DoctorRating
├── ratingId: String
├── doctorId: String
├── consultationId: String
├── patientSessionId: String
├── rating: Int                   // 1-5
├── comment: String?
├── createdAt: Long
└── synced: Boolean               // Default false

DoctorEarnings
├── earningId: String
├── doctorId: String
├── consultationId: String
├── amount: Int                   // Smallest currency unit
├── status: EarningStatus         // PENDING or PAID
├── paidAt: Long?
└── createdAt: Long
```

### 3.5 Payment Models

```
Payment
├── paymentId: String
├── patientSessionId: String
├── amount: Int                   // Smallest currency unit (TZS)
├── paymentMethod: PaymentMethod  // MPESA only currently
├── transactionId: String?        // From payment gateway
├── phoneNumber: String           // For M-Pesa
├── status: PaymentStatus         // Default: PENDING
├── failureReason: String?
├── createdAt: Long
├── updatedAt: Long
└── synced: Boolean               // Default false

PaymentStatus (enum): PENDING, COMPLETED, FAILED, CANCELLED
PaymentMethod (enum): MPESA
```

### 3.6 Communication Models

```
VideoCall
├── callId: String
├── consultationId: String
├── startedAt: Long
├── endedAt: Long?
├── durationSeconds: Int
├── callQuality: CallQuality      // EXCELLENT, GOOD, FAIR, POOR
├── meetingId: String
├── initiatedBy: String
├── callType: String              // "VIDEO" or "AUDIO"
├── status: VideoCallStatus
├── timeLimitSeconds: Int         // Default 180 (3 min)
├── timeUsedSeconds: Int
├── isTimeExpired: Boolean
└── totalRecharges: Int

VideoCallStatus (enum): INITIATED, RINGING, CONNECTED, COMPLETED, MISSED, DECLINED, FAILED
CallType (enum): AUDIO, VIDEO

Notification
├── notificationId: String
├── userId: String
├── title: String
├── body: String
├── type: NotificationType
├── data: String                  // JSON
├── readAt: Long?
└── createdAt: Long

NotificationType (enum):
  CONSULTATION_REQUEST, CONSULTATION_ACCEPTED, MESSAGE_RECEIVED,
  VIDEO_CALL_INCOMING, REPORT_READY, PAYMENT_STATUS,
  DOCTOR_APPROVED, DOCTOR_REJECTED, DOCTOR_WARNED, DOCTOR_SUSPENDED,
  DOCTOR_UNSUSPENDED, DOCTOR_BANNED, DOCTOR_UNBANNED,
  APPOINTMENT_CONFIRMED, APPOINTMENT_REMINDER, APPOINTMENT_MISSED,
  APPOINTMENT_RESCHEDULED, APPOINTMENT_CANCELLED

MessageData
├── messageId: String
├── consultationId: String
├── senderType: String            // "patient" or "doctor"
├── senderId: String
├── messageText: String
├── messageType: String           // "text", "image", "file"
├── attachmentUrl: String?
├── isRead: Boolean
├── synced: Boolean
├── createdAt: Long
├── retryCount: Int
└── failedAt: Long?
```

### 3.7 Reports & Medical Records

```
PatientReport
├── reportId: String
├── consultationId: String
├── patientSessionId: String
├── reportUrl: String
├── localFilePath: String?
├── generatedAt: Long
├── downloadedAt: Long?
├── fileSizeBytes: Long
├── isDownloaded: Boolean
├── doctorName: String
├── consultationDate: Long
├── diagnosedProblem: String
├── category: String
├── severity: String              // Mild, Moderate, Severe
├── presentingSymptoms: String
├── diagnosisAssessment: String
├── treatmentPlan: String
├── followUpInstructions: String
├── followUpRecommended: Boolean
├── furtherNotes: String
└── verificationCode: String
```

### 3.8 Security & Configuration

```
SecurityQuestion
├── key: String                   // e.g. "first_pet"
└── label: String                 // Display text

Question Keys:
  - first_pet, favorite_city, birth_city, primary_school, favorite_teacher

Service Tier (Prepopulated)
├── id: String                    // e.g. "tier_nurse"
├── category: String              // "NURSE", "GP", etc.
├── displayName: String
├── description: String
├── priceAmount: Int              // TZS
├── currency: String              // "TZS"
├── isActive: Boolean
├── sortOrder: Int
├── durationMinutes: Int
└── features: String              // Comma-separated

Prepopulated Tiers:
  tier_nurse:             5,000 TZS, 15 min
  tier_clinical_officer:  7,000 TZS, 15 min
  tier_pharmacist:        3,000 TZS,  5 min
  tier_gp:               10,000 TZS, 15 min
  tier_specialist:       30,000 TZS, 15 min
  tier_psychologist:     50,000 TZS, 20 min
  tier_herbalist:         5,000 TZS, 15 min
  tier_drug_interaction:  5,000 TZS,  5 min
```

---

## 4. Repository Interfaces & Methods

### 4.1 AuthRepository
```
Properties:
  currentSession: Flow<Session?>          // Observable session

Methods:
  createPatientSession() → Result<Session>
  registerDoctor(registration) → Result<Session>
  loginDoctor(email, password) → Result<Session>
  refreshSession() → Result<Session>
  logout()
  recoverPatientSession(answers: Map<String,String>) → Result<Session>
  setupSecurityQuestions(answers: Map<String,String>) → Result<Unit>
  lookupPatientById(patientId: String) → Result<Session>
```

### 4.2 ConsultationRepository
```
  getConsultationsForPatient(patientId) → Flow<[Consultation]>
  getConsultationsForDoctor(doctorId) → Flow<[Consultation]>
  createConsultation(patientId, serviceType) → Result<Consultation>
  bookAppointment(doctorId, serviceType, consultationType, chiefComplaint, preferredLanguage) → Result<Consultation>
  updateConsultationStatus(consultationId, status) → Result<Consultation>
  getConsultation(consultationId) → Result<Consultation>
```

### 4.3 ConsultationRequestRepository
```
  createRequest(doctorId, serviceType, consultationType, chiefComplaint,
                symptoms?, patientAgeGroup?, patientSex?, patientBloodGroup?,
                patientAllergies?, patientChronicConditions?) → Result<ConsultationRequest>
  acceptRequest(requestId) → Result<ConsultationRequest>
  rejectRequest(requestId) → Result<ConsultationRequest>
  expireRequest(requestId) → Result<ConsultationRequest>
  checkRequestStatus(requestId) → Result<ConsultationRequest>
```

### 4.4 AppointmentRepository
```
  bookAppointment(doctorId, scheduledAt, durationMinutes, serviceType,
                  consultationType, chiefComplaint) → Result<Appointment>
  cancelAppointment(appointmentId) → Result<Appointment>
  rescheduleAppointment(appointmentId, newScheduledAt, reason) → Result<Appointment>
  getAvailableSlots(doctorId, date) → Result<AvailableSlotsResponse>
  getAppointments(status?, limit=50, offset=0) → Result<[Appointment]>
  syncAppointments() → Result<Unit>
```

### 4.5 PaymentRepository
```
  getPaymentsBySession(sessionId) → Flow<[Payment]>
  getPaymentsByStatus(status) → Flow<[Payment]>
  getTransactionHistory(limit=20, offset=0) → Flow<[Payment]>
  getPaymentById(paymentId) → Payment?
  createPayment(payment) → Result<Payment>
  updatePaymentStatus(paymentId, status, transactionId?) → Unit
  getUnsyncedPayments() → [Payment]
  markPaymentSynced(paymentId) → Unit
  clearAll() → Unit
```

### 4.6 MessageRepository
```
  getByConsultationId(consultationId) → Flow<[MessageData]>
  saveMessage(message) → Unit
  saveMessages(messages: [MessageData]) → Unit
  markAsRead(messageId) → Unit
  markAsSynced(messageId) → Unit
  getUnsyncedMessages() → [MessageData]
  getLatestSyncedTimestamp(consultationId) → Long?
  clearAll() → Unit
```

### 4.7 DoctorProfileRepository
```
  getAllDoctors() → Flow<[DoctorProfile]>
  getDoctorById(doctorId) → DoctorProfile?
  getDoctorsBySpecialty(specialty) → Flow<[DoctorProfile]>
  getAvailableDoctors() → Flow<[DoctorProfile]>
  getDoctorsByRatingRange(minRating) → Flow<[DoctorProfile]>
  updateAvailability(doctorId, isAvailable) → Unit
  updateRating(doctorId, averageRating, totalRatings) → Unit
  getDoctorAvailability(doctorId) → Flow<DoctorAvailability?>
  getDoctorCredentials(doctorId) → Flow<[DoctorCredentials]>
  refreshDoctors() → Unit
  isCacheStale() → Boolean                // >1 hour
  clearAll() → Unit
```

### 4.8 Other Repositories
```
NotificationRepository:
  getNotificationsForUser(userId) → Flow<[Notification]>
  getUnreadNotifications(userId) → Flow<[Notification]>
  getUnreadCount(userId) → Flow<Int>
  markAsRead(notificationId) / markAllAsRead(userId)
  fetchAndStoreNotification(notificationId)
  syncFromRemote(userId)

VideoCallRepository:
  getVideoCallById(callId) → VideoCall?
  getVideoCallsByConsultation(consultationId) → Flow<[VideoCall]>
  saveVideoCall(videoCall) / deleteVideoCall(videoCall)

VideoRepository:
  getVideoToken(consultationId, callType?, roomId?) → Result<VideoToken>

PatientReportRepository:
  getReportById(reportId) → PatientReport?
  getReportsByPatientSession(patientSessionId) → Flow<[PatientReport]>
  fetchReportsFromServer() → [PatientReport]
  markAsDownloaded(reportId, localFilePath)

DoctorRatingRepository:
  submitRating(rating) / hasRating(consultationId) → Boolean
  submitRatingToServer(rating) → Boolean
  getRatingsForDoctor(doctorId) → Flow<[DoctorRating]>
  getUnsyncedRatings() → [DoctorRating]

DoctorEarningsRepository:
  getEarningsForDoctor(doctorId) → Flow<[DoctorEarnings]>
  getTotalEarnings(doctorId, startDate, endDate) → Flow<Int>

FcmTokenRepository:
  registerToken(token, userId) / getStoredToken() → String?
  fetchAndRegisterToken(userId)

CallRechargeRepository:
  submitRecharge(consultationId, minutes, phoneNumber) → Boolean

PatientSessionRepository:
  getSession() → Flow<PatientSession?>
  saveSession(session) / updateMedicalInfo(sessionId, allergies, chronicConditions)
  clearSession()

SecurityQuestionRepository:
  getSecurityQuestions() → Result<[SecurityQuestion]>
```

---

## 5. Use Cases (Business Logic)

Each use case wraps a single repository call with a `suspend operator fun invoke()`:

| Use Case | Action |
|----------|--------|
| `CreatePatientSessionUseCase` | `authRepository.createPatientSession()` |
| `LoginDoctorUseCase(email, password)` | `authRepository.loginDoctor()` |
| `RegisterDoctorUseCase(registration)` | `authRepository.registerDoctor()` |
| `LogoutUseCase` | `authRepository.logout()` |
| `ObserveAuthStateUseCase` | Maps `authRepository.currentSession` → `Flow<AuthState>` |
| `RefreshSessionUseCase` | `authRepository.refreshSession()` |
| `CreateConsultationUseCase(patientId, serviceType)` | `consultationRepository.createConsultation()` |
| `InitiatePaymentUseCase(payment)` | `paymentRepository.createPayment()` |
| `GetVideoTokenUseCase(consultationId)` | `videoRepository.getVideoToken()` |
| `GetSecurityQuestionsUseCase` | `securityQuestionRepository.getSecurityQuestions()` |
| `SetupSecurityQuestionsUseCase(answers)` | `authRepository.setupSecurityQuestions()` |
| `RecoverPatientSessionUseCase(answers)` | `authRepository.recoverPatientSession()` |
| `CreatePatientSessionUseCase` | `authRepository.createPatientSession()` |

---

## 6. Authentication Feature

### 6.1 Navigation Flow

```
Splash → Language Selection → Role Selection →
  ├─ PATIENT PATH:
  │   ├─ Terms Agreement → Patient Setup → [Security Questions] → Patient Dashboard
  │   ├─ "I have my ID" → Access Records (lookup by ID) → Patient Dashboard
  │   └─ "Forgot Patient ID?" → Recovery (5 security questions) → Patient Dashboard
  │
  └─ DOCTOR PATH:
      ├─ Doctor Terms → Doctor Registration (8 steps) → Doctor Dashboard
      └─ Doctor Login → [Account Status Checks] → Doctor Dashboard
```

### 6.2 Splash Screen
- Earth logo (120dp), fade-in animation (3s)
- Tap to continue after 2s delay
- Checks if locale is already set → skip language selection

### 6.3 Language Selection
- Two buttons: Swahili (filled teal) vs English (outlined)
- Detailed picker with 37 languages (6 fully supported, 31 coming soon)
- Supported: Arabic, English, Spanish, French, Hindi, Swahili

### 6.4 Role Selection
- **Patient options**: "Are you new?", "I have my ID", "Forgot Patient ID?"
- **Doctor options**: "Sign In", "Sign Up", "Forgot Password?"

### 6.5 Terms & Conditions (5 tabs)
1. Privacy Policy
2. Data Security
3. Terms of Service
4. Medical Disclaimer
5. Informed Consent
- Checkbox required + "Agree & Continue" button

### 6.6 Patient Setup Screen
**Components:**
1. **Patient ID Card** (teal, shows ESR-XXXXX, copy button)
2. **Download ID Card** (generates PDF with watermark)
3. **Recovery Questions** card (lock/checkmark icon)
4. **Health Profile**: Sex (chips), Age Group (dropdown: 8 options), Blood Type (8 types), Allergies (text), Chronic Conditions (text)
5. **Continue** button

**Business Logic:**
- Auto-creates session via `create-patient-session` edge function on init
- Auto-detects region from GPS (reverse geocoding)
- Syncs demographics to `update-patient-demographics` edge function
- PDF ID card: teal header, patient ID, health profile, diagonal watermark

**State:**
```
PatientSetupUiState:
  patientId, recoveryQuestionsCompleted, sex, ageGroup, bloodType,
  allergies, chronicConditions, region (auto-detected),
  isCreatingSession, isSaving, isGeneratingPdf,
  sessionError?, saveError?, pdfError?,
  isComplete, canDownloadPdf (derived)
```

### 6.7 Security Questions Setup
- 5 questions cycled (first_pet, favorite_city, birth_city, primary_school, favorite_teacher)
- Progress bar + answer field + Next/Save button
- Skip button available
- All answers submitted at once on last question

### 6.8 Doctor Login
**Form:** Email + Password (visibility toggle)
**Validation:** Valid email + 6+ char password

**Account Status Checks (post-login):**
1. Device Mismatch → error screen with lock icon
2. Banned → red X, ban reason, 7-day appeal window, support email
3. Suspended → orange pause, countdown timer, lift date, support email
4. Warning → amber warning, acknowledge button required before proceeding

### 6.9 Doctor Registration (8 Steps)
1. **Account**: Email, Password, Confirm Password
2. **OTP Verification**: 6-digit code via email, 60s resend cooldown
3. **Profile Photo**: Optional camera/gallery picker
4. **Personal Info**: Full name, phone (with country code), specialty dropdown
5. **Location & Languages**: Country, language checkboxes
6. **Professional Details**: License number, years experience (0-70), bio (10-1000 chars)
7. **Services**: Checkbox list of offered services
8. **Credentials**: License document upload, certificates upload

**Validation per step:**
- Email: regex `^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$`
- Password: 8+ chars, 1+ uppercase, 1+ digit
- Name: 2-100 chars
- Phone: 7-15 digits
- Bio: 10-1000 chars
- Experience: 0-70 integer

**Registration flow:**
1. Call `register-doctor` edge function
2. Save session + tokens
3. Upload files (profile photo, license, certificates) to Supabase Storage
4. Update doctor profile with file URLs
5. Cache profile locally

### 6.10 Patient Recovery
- Answer 5 security questions sequentially
- 3/5 correct answers required
- Rate limited: 10 attempts per 30 minutes
- On success: returns patient ID + new session tokens

### 6.11 Access Records (Lookup by ID)
- Enter Patient ID (ESR-XXXXXX-XXXX format)
- Check local DB first, then `recover-by-id` edge function
- Returns session for existing patient

### 6.12 Auth Repository Implementation Details
**createPatientSession:**
- Edge function: `create-patient-session` (anonymous, no auth)
- Returns: session_id, patient_id, access_token, refresh_token, expires_at

**registerDoctor:**
1. Call `register-doctor` edge function
2. Save tokens via TokenManager
3. Import auth token to Supabase client
4. Upload files to Supabase Storage buckets (profile-photos, credentials)
5. Update doctor profile URLs
6. Cache doctor profile in local DB

**loginDoctor:**
- Edge function: `login-doctor`
- Caches doctor profile locally after success

**logout:**
- Best-effort server call
- Clear all tokens
- Wipe local DB
- Reseed reference data (service tiers, app config)

**Token types:**
- Patient: Custom HS256 JWT (role="patient" or app_role="patient"). NOT refreshable via Supabase Auth.
- Doctor: Standard Supabase Auth JWT. Refreshable via `/auth/v1/token`.

---

## 7. Patient Feature

### 7.1 Navigation Routes
```
PatientHomeRoute → ServiceLocationRoute → ServicesRoute → FindDoctorRoute →
  ├─ BookAppointmentRoute → PatientAppointmentsRoute
  └─ (Request Consultation) → PatientConsultationRoute → PatientPaymentRoute →
       PatientVideoCallRoute → (Rating)

PatientHomeRoute → ConsultationHistoryRoute
PatientHomeRoute → ReportsRoute → ReportDetailRoute
PatientHomeRoute → PatientProfileRoute
PatientHomeRoute → ExtensionPaymentRoute (from active consultation)
```

### 7.2 Patient Home Screen
**Components:**
- Welcome header (masked patient ID: "ESR-******-P8FP", copy button)
- Settings row (sounds toggle, language switch, logout)
- Medical info section (edit button → profile)
- Quick action chips: Services, New Consultation, Reports
- Start Consultation card
- Dashboard cards: Consultation History, Reports, Appointments
- **Active Chat FAB** (pulsing animation when consultation active)
- Logout confirmation dialog
- Pending rating bottom sheet (unrated completed consultations)

**Key Logic:**
- `maskPatientId(id)` - Keep prefix + last segment, mask middle
- `checkPendingRatings()` - Query DB for unrated completed consultations
- `syncUnsyncedRatings()` - Retry offline ratings on load

### 7.3 Service Location Screen
- Location permission check
- "Inside Tanzania" card → Services screen
- "Outside Tanzania" card → "Not available yet" dialog

### 7.4 Services Screen
- Lists 8 prepopulated service tiers from local DB
- Shows: category icon, name, description, price (TZS), duration
- Select → FindDoctorRoute(category, price, duration)

### 7.5 Find Doctor Screen
**Components:**
- Search bar (by name/specialty/bio)
- Availability filter (ALL, ONLINE, OFFLINE)
- Doctor cards: photo, name, specialty, rating stars, verified badge
- Two buttons per doctor:
  - "Book Appointment" → BookAppointmentRoute
  - "Request Consultation" → opens Symptoms Dialog

**Doctor Loading:**
- Fetches from backend via `list-doctors` edge function
- Caches to local DB
- Deletes stale entries not in fresh response

**Consultation Request Flow:**
1. Tap "Request Consultation" → SymptomsEntryDialog
2. Enter symptoms (optional), confirm
3. Send to `handle-consultation-request` (action: create)
4. Start 60s countdown + server polling every 3s
5. Subscribe to Realtime for status changes
6. ACCEPTED → navigate to PatientConsultationRoute
7. REJECTED/EXPIRED → show message, auto-dismiss after 3s

### 7.6 Book Appointment Screen
**Components:**
- Doctor info card (name, specialty, rating, verified)
- Date picker: horizontal scroll of next 14 days as pills
- Time slots: grid of available times (15/30-min slots with buffers)
- Chief complaint text field (min 10 chars)
- "Book Appointment" button

**Slot Generation Logic:**
1. Fetch doctor's availability slots for selected day-of-week
2. Query booked appointments for that date
3. Generate time slots from availability windows
4. Filter past times (if today)
5. Check overlap with existing bookings
6. Mark unavailable slots as disabled

**Timezone:** EAT (Africa/Nairobi)

### 7.7 Patient Appointments Screen
- Tab bar: UPCOMING | PAST
- UPCOMING: status in (BOOKED, CONFIRMED, IN_PROGRESS)
- PAST: status in (COMPLETED, MISSED, CANCELLED, RESCHEDULED)
- Cancel button for upcoming appointments

### 7.8 Patient Consultation Screen (Active Chat)
**Components:**
- Chat message list (scrollable)
- Input field + send button
- Typing indicator ("Doctor is typing...")
- Attachment menu: Camera, Image Gallery, Document (PDF only)
- Call menu: Voice Call, Video Call
- Consultation timer bar (remaining time + extension count)
- Patient extension prompt (accept/decline doctor's extension request)
- Rating bottom sheet (after consultation ends)

**Message Sending (Optimistic):**
1. Insert to local DB immediately
2. Send to `handle-messages` edge function
3. Mark as synced on success
4. Show error banner on failure, queue for retry

**Attachment Upload:**
1. Check MIME type (image/* or application/pdf only, max 10MB)
2. Apply eSIRI watermark (WatermarkUtil)
3. Upload to Supabase storage (message-attachments bucket)
4. Create message with attachmentUrl

**Typing Indicator:**
- Throttle: 2s between typing signals
- Auto-clear: 3s after last typing
- Display timeout: 5s (hide "X is typing" after)

**Polling:**
- Connected to Realtime: slow poll every 30s
- Disconnected: fast poll every 3s

**Phase Transitions:**
- ACTIVE → normal chat
- GRACE_PERIOD → navigate to ExtensionPaymentScreen
- COMPLETED → show RatingBottomSheet

### 7.9 Patient Payment Screen
**States:** CONFIRM → PROCESSING → COMPLETED/FAILED

**Flow:**
1. CONFIRM: Show amount, "Pay Now" button
2. PROCESSING: Spinner, "Processing via M-Pesa..."
3. Poll every 3s for up to 2 min (40 iterations)
4. COMPLETED: Check mark, auto-navigate after 1.5s
5. FAILED: Error + retry button

**Back button blocked during PROCESSING & COMPLETED**

### 7.10 Extension Payment Screen
- Same flow as payment, but for extending consultation time
- On success: notify ConsultationSessionManager → continue chat

### 7.11 Rating Bottom Sheet
- 5-star picker with labels: Poor, Fair, Good, Very Good, Excellent
- Comment field (required if rating ≤3 stars)
- Submit → save locally + sync to server
- Auto-dismiss after 1.5s on success

### 7.12 Reports Screen
- List of medical reports (doctor name, date, diagnosis, category)
- Fetch from `get-patient-reports` edge function
- Report detail view with all fields
- Download as PDF + share via system share sheet

### 7.13 Patient Profile Screen
- Sex filter chips, Age group dropdown, Blood type dropdown
- Allergies + chronic conditions text fields
- Save to local Room DB

---

## 8. Doctor Feature

### 8.1 Navigation Routes
```
DoctorDashboardRoute (main hub with sidebar)
├─ DoctorConsultationListRoute → DoctorConsultationDetailRoute →
│   ├─ DoctorVideoCallRoute
│   └─ DoctorReportRoute (bottom sheet)
├─ DoctorAppointmentsRoute
├─ DoctorAvailabilitySettingsRoute
└─ DoctorNotificationsRoute
```

### 8.2 Doctor Dashboard Screen
**Sidebar Navigation:** Dashboard, Consultations, Chat, Availability, Profile, Earnings
**Content Tabs:** DASHBOARD, CONSULTATIONS, CHAT, AVAILABILITY, PROFILE, EARNINGS

**Dashboard Tab:**
- Pending requests count
- Active consultations count
- Today's earnings (TZS)
- Total patients count
- Active consultation resume banner (crash recovery)

**Key Logic:**
- Toggle online/offline status → updates DoctorOnlineService
- Load profile from local DB + fetch from Supabase
- Subscribe to Realtime for profile/status changes
- Sign-out confirmation dialog

### 8.3 Doctor Consultation List
- All consultations with status badges
- Status colors: ACTIVE=Teal, COMPLETED=Green, else=Orange
- Sorted by creation time descending

### 8.4 Doctor Consultation Detail (Chat)
**Components:**
- Chat messages (same as patient side)
- Top bar actions:
  - Phone dropdown: Audio/Video call
  - Edit: Write report (bottom sheet)
  - Description: Generate patient summary
  - Close (red): End consultation
- Attachment menu (camera, gallery, PDF)
- Consultation timer bar
- Extension overlay (if AWAITING_EXTENSION)
- Grace period banner

**Back handler disabled during active consultation**

**Report Bottom Sheet (post-consultation):**
- Diagnosed Problem (required, 3-line)
- Category dropdown (10 options: General Medicine, Neurological, Cardiovascular, Respiratory, Gastrointestinal, Musculoskeletal, Dermatological, Mental Health, Infectious Disease, Other)
- Severity dropdown (Mild, Moderate, Severe)
- Treatment Plan (required)
- Further Notes (optional)
- Follow Up Recommended (checkbox)
- Submits to `generate-consultation-report` edge function

### 8.5 Doctor Appointments
- Tabs: TODAY (n), UPCOMING (n), MISSED (n)
- "Start Session" button → creates consultation or reuses existing
- "Reschedule" button for missed (dialog with new time + reason)

### 8.6 Doctor Availability Settings
- Add/delete time slots per day of week
- Each slot: day, start time, end time, buffer minutes
- Sound settings: incoming call ringtone, consultation request ringtone

### 8.7 Doctor Notifications
- Notification list with read/unread status
- Type-based icons: Approved=Green check, Rejected/Banned=Red warning, Suspended=Orange warning
- Mark as read (single/all)

### 8.8 Incoming Request Dialog (Modal)
- 60-second countdown timer
- Patient info card (symptoms, age, sex, blood group, allergies, conditions)
- Accept/Decline buttons
- Auto-dismiss on timeout
- Retry on failure with error message

### 8.9 Patient Summary (AI-Generated)
- Calls `generate-patient-summary` edge function
- Shows sections: Overview, Medical History, Current Conditions, Treatment, Vitals, Recommendations

---

## 9. Chat & Video Call Feature

### 9.1 Chat Components
- Message list with sender differentiation (patient vs doctor)
- Text input with send button
- Typing indicator with throttle (2s) and auto-clear (3s)
- Attachment support (images, PDFs with watermarking)

### 9.2 Video Call
- Uses VideoSDK for real-time video/audio
- Token fetched from `videosdk-token` edge function
- Call types: AUDIO, VIDEO
- Timer: default 180s (3 min), recharge for more time
- Call quality tracking: EXCELLENT, GOOD, FAIR, POOR

### 9.3 Call Recharge
- Packages: 10min/200TZS, 30min/500TZS, 60min/900TZS, 120min/1500TZS
- M-Pesa STK Push payment
- On success: adds minutes to active call via `add_call_minutes` RPC

### 9.4 Consultation Session Manager
- Tracks consultation phase: ACTIVE → AWAITING_EXTENSION → GRACE_PERIOD → COMPLETED
- Timer countdown with server sync
- Extension flow: Doctor requests → Patient accepts/declines → Payment if accepted

### 9.5 Consultation Timer Bar
- Shows remaining time + extension count
- Syncs with server via `manage-consultation` (action: sync)

---

## 10. Network Layer

### 10.1 HTTP Client Configuration
- **Timeouts:** Connect=30s, Read=60s, Write=60s
- **Connection Pool:** 15 idle connections, 10 min keep-alive
- **HTTP Cache:** 10 MB
- **Certificate Pinning:** Configured for Supabase domain

### 10.2 Interceptor Chain (Order Matters)
1. **CircuitBreakerInterceptor** - Fail-fast on cascading failures
   - States: CLOSED → OPEN (5 consecutive failures, 30s) → HALF_OPEN (1 probe)
   - Categories: payment, video, consultation, messages, rest, edge, default

2. **ClientRateLimiterInterceptor** - Rate limiting per endpoint

3. **MetricsInterceptor** - Request/response metrics collection

4. **ProactiveTokenRefreshInterceptor** - Refresh token if expiring in <5 min
   - Only for doctor (Supabase Auth) tokens, NOT patient custom JWTs

5. **AuthInterceptor** - Authorization header injection
   - Always adds `apikey: SUPABASE_ANON_KEY`
   - Doctor tokens: `Authorization: Bearer <token>`
   - Patient tokens on edge functions: `Authorization: Bearer <anon_key>` + `X-Patient-Token: <patient_jwt>`
   - Patient tokens on non-edge: NO Authorization (uses anon role RLS)

6. **RetryInterceptor** - Retry with exponential backoff
   - Max retries: 3
   - Retryable: 408, 429, 500-599
   - Backoff: 1s → 2s → 4s → 8s (max 15s)
   - Respects `Retry-After` header

7. **LoggingInterceptor** - HTTP logging

8. **TokenRefreshAuthenticator** - On 401 response
   - One retry with refreshed token
   - Skips patient custom JWTs (can't refresh)
   - After 2nd 401: invalidates session

### 10.3 Token Management
**TokenManager:**
- Stores access/refresh tokens securely
- `getAccessTokenSync()`, `getRefreshTokenSync()`, `saveTokens()`, `clearTokens()`
- `isTokenExpiringSoon(thresholdMinutes = 5)` - checks JWT expiry

**Token Types:**
- Patient: Custom HS256 JWT with claims `{session_id, role:"authenticated", app_role:"patient"}`
- Doctor: Standard Supabase Auth JWT

**iOS equivalent:** Use Keychain for secure token storage

### 10.4 EdgeFunctionClient
- Base URL: `{SUPABASE_URL}/functions/v1/{functionName}`
- All requests POST with JSON body
- Three auth modes:
  1. **Doctor**: `Authorization: Bearer <supabase_jwt>`
  2. **Anonymous**: `Authorization: Bearer <anon_key>` + `X-Skip-Auth: true`
  3. **Patient**: `Authorization: Bearer <anon_key>` + `X-Patient-Token: <patient_jwt>`

### 10.5 PostgREST API Endpoints (Retrofit)
```
GET  /rest/v1/consultations?patient_id=eq.{id}&order=created_at.desc
GET  /rest/v1/consultations?doctor_id=eq.{id}&order=created_at.desc
GET  /rest/v1/consultations?id=eq.{id}   (Accept: application/vnd.pgrst.object+json)
PATCH /rest/v1/consultations?id=eq.{id}  with {status: "..."}

GET  /rest/v1/payments?consultation_id=eq.{id}&order=created_at.desc
GET  /rest/v1/payments?payment_id=eq.{id}

GET  /rest/v1/users?id=eq.{id}
PATCH /rest/v1/users?id=eq.{id}

POST /rest/v1/fcm_tokens  (resolution=merge-duplicates)
```

### 10.6 DTO Models

**Auth DTOs:**
```
PatientSessionResponse: sessionId, patientId, access_token, refresh_token, expires_at/in
SessionResponse: access_token, refresh_token, expires_at, user(UserDto)
DoctorRegistrationRequest: email, password, fullName, countryCode, phone, specialty,
                           country, languages, licenseNumber, yearsExperience, bio,
                           services, profilePhotoUrl?, licenseDocumentUrl?, certificatesUrl?
DoctorLoginRequest: email, password
```

**Payment DTOs:**
```
StkPushRequest: phoneNumber, amount, consultationId?, paymentType, serviceType?, idempotencyKey
StkPushResponse: message, paymentId, checkoutRequestId, paymentEnv?, status?
```

**Video DTOs:**
```
VideoTokenRequest: consultationId, callType?, roomId?
VideoTokenResponse: token, roomId, permissions, expiresIn=7200s
```

### 10.7 Error Handling
```
ApiResult<T> (sealed):
├── Success<T>(data)
├── Error(code, message)
├── NetworkError(exception)
└── Unauthorized

ErrorCode (enum with HTTP mappings):
  BAD_REQUEST(400), UNAUTHORIZED(401), FORBIDDEN(403), NOT_FOUND(404),
  REQUEST_TIMEOUT(408), CONFLICT(409), UNPROCESSABLE_ENTITY(422),
  RATE_LIMITED(429), SERVER_ERROR(500-599),
  NO_INTERNET, CONNECTION_TIMEOUT, NETWORK_ERROR, UNEXPECTED
```

### 10.8 Realtime Subscriptions
- **ChatRealtimeService** - Real-time message updates
- **ConsultationRequestRealtimeService** - Request status changes (ACCEPTED/REJECTED/EXPIRED)
- **ConsultationStatusRealtimeService** - Consultation phase changes
- **DoctorRealtimeService** - Doctor status/profile updates

### 10.9 File Upload
```
FileUploadService:
  uploadFile(bucketName, path, bytes, contentType) → ApiResult<String>
  getPublicUrl(bucketName, path) → String

Buckets: profile-photos, credentials, message-attachments
Path patterns: {userId}/profile-photo, {userId}/license-document, {consultationId}/{uuid}.{ext}
```

---

## 11. Database Layer

### 11.1 Overview
- **Android:** Room 2.7.1 with SQLCipher encryption (AES-256-GCM)
- **iOS equivalent:** CoreData/SwiftData with SQLCipher or Realm with encryption
- **Version:** 24 (with incremental migrations)
- **Entities:** 30 tables

### 11.2 Entity List
```
Core:           users, sessions, app_config, providers, audit_logs
Patient:        patient_sessions, patient_profiles, patient_reports
Doctor:         doctor_profiles, doctor_credentials, doctor_availability,
                doctor_availability_slots, schedules, doctor_ratings, doctor_earnings
Consultation:   consultations, appointments
Communication:  messages, attachments, typing_indicators, notifications, video_calls
Medical:        prescriptions, diagnoses, vital_signs, medical_records, reviews
Payment:        payments, service_access_payments, call_recharge_payments
Config:         service_tiers
```

### 11.3 Key Entity Schemas

**ConsultationEntity:**
```
consultationId (PK), patientSessionId (indexed), doctorId (indexed),
status (indexed), serviceType, consultationFee, sessionStartTime?,
sessionEndTime?, sessionDurationMinutes=15, requestExpiresAt,
createdAt (indexed), updatedAt, scheduledEndAt?, extensionCount=0,
gracePeriodEndAt?, originalDurationMinutes=15
```

**AppointmentEntity:**
```
appointmentId (PK), doctorId (indexed), patientSessionId (indexed),
scheduledAt (indexed), durationMinutes=15, status, serviceType,
consultationType="chat", chiefComplaint="", consultationFee=0,
consultationId?, rescheduledFrom?, reminderSentAt?, createdAt, updatedAt
```

**DoctorProfileEntity:**
```
doctorId (PK), fullName, email (indexed), phone, specialty (indexed),
specialistField?, languages: [String], bio, licenseNumber,
yearsExperience, profilePhotoUrl?, averageRating=0.0 (indexed),
totalRatings=0, isVerified=false (indexed), isAvailable=false (indexed),
createdAt, updatedAt, services: [String], countryCode="+255",
country="", licenseDocumentUrl?, certificatesUrl?, rejectionReason?,
isBanned=false, bannedAt?, banReason?, inSession=false,
maxAppointmentsPerDay=10
```

### 11.4 Prepopulated Data
On first DB creation and after `clearAllTables()`:
- 8 service tiers (nurse through drug_interaction)
- 5 app config entries (session_timeout, max_file_size, etc.)

### 11.5 Type Converters
- `Instant ↔ Long` (epoch milliseconds)
- `List<String> ↔ JSON String`
- `JsonObject/Map<String,String> ↔ JSON String`

### 11.6 Relations
- ConsultationWithMessages, ConsultationWithDoctor
- DoctorWithCredentials, PatientWithConsultations

---

## 12. Supabase Edge Functions (Backend API)

### 12.1 Authentication Functions

| Function | Method | Auth | Purpose |
|----------|--------|------|---------|
| `create-patient-session` | POST | Public | Create anonymous patient session |
| `refresh-patient-session` | POST | Public | Refresh patient JWT (single-use refresh token) |
| `extend-session` | POST | Patient | Extend session by 1-72 hours |
| `recover-by-id` | POST | Public | Recover session via Patient ID |
| `recover-by-questions` | POST | Public | Recover via security questions (3/5 correct) |
| `setup-recovery` | POST | Patient | Set up 5 recovery questions |
| `login-doctor` | POST | Public | Doctor email+password login |
| `register-doctor` | POST | Public | Create doctor account |
| `send-doctor-otp` | POST | Public | Send email OTP (6-digit, 10-min expiry) |
| `verify-doctor-otp` | POST | Public | Verify email OTP |

### 12.2 Consultation Functions

| Function | Method | Auth | Purpose |
|----------|--------|------|---------|
| `create-consultation` | POST | Patient | Create consultation request |
| `handle-consultation-request` | POST | Both | Multi-action: create/accept/reject/expire/status |
| `manage-consultation` | POST | Both | Multi-action: sync/end/timer_expired/request_extension/accept_extension/decline_extension/payment_confirmed/cancel_payment |

**Service Fees (charged at accept):**
```
nurse: 5,000 TZS    clinical_officer: 7,000 TZS    pharmacist: 3,000 TZS
gp: 10,000 TZS      specialist: 30,000 TZS         psychologist: 50,000 TZS
```

**Service Durations:**
```
nurse: 15 min    clinical_officer: 15 min    pharmacist: 5 min
gp: 15 min       specialist: 20 min          psychologist: 30 min
```

### 12.3 Messaging Functions

| Function | Method | Auth | Purpose |
|----------|--------|------|---------|
| `handle-messages` | POST | Both | Multi-action: get/send/typing/mark_read |

### 12.4 Payment Functions

| Function | Method | Auth | Purpose |
|----------|--------|------|---------|
| `service-access-payment` | POST | Patient | Initiate service access payment |
| `call-recharge-payment` | POST | Patient | Add minutes to video call |
| `mpesa-stk-push` | POST | Both | Initiate M-Pesa STK Push |
| `mpesa-callback` | POST | Public (IP allowlist) | Receive payment confirmation |

**Payment Types:** service_access, call_recharge
**Recharge Packages:** 10min/200TZS, 30min/500TZS, 60min/900TZS, 120min/1500TZS

### 12.5 Appointment Functions

| Function | Method | Auth | Purpose |
|----------|--------|------|---------|
| `book-appointment` | POST | Both | Multi-action: book/cancel/get_slots/get_appointments |
| `reschedule-appointment` | POST | Both | Reschedule to new time |

### 12.6 Doctor Management Functions

| Function | Method | Auth | Purpose |
|----------|--------|------|---------|
| `list-doctors` | GET | Public | List verified available doctors (cached 30s) |
| `list-all-doctors` | GET | Admin/HR | All doctors with stats |
| `get-doctor-slots` | POST | Public | Batch availability check (cached 15s) |
| `manage-doctor` | POST | Admin | approve/reject/suspend/unsuspend/ban/unban/warn |

### 12.7 Notification Functions

| Function | Method | Auth | Purpose |
|----------|--------|------|---------|
| `send-push-notification` | POST | Auth/Internal | Send FCM push to patient/doctor/broadcast |
| `update-fcm-token` | POST | Both | Register device token |

### 12.8 Report & Analytics Functions

| Function | Method | Auth | Purpose |
|----------|--------|------|---------|
| `generate-consultation-report` | POST | Doctor | Submit post-consultation report |
| `generate-patient-summary` | POST | Doctor | AI-generated patient summary |
| `get-patient-reports` | GET | Patient | Fetch reports list |
| `rate-doctor` | POST | Patient | Submit 1-5 star rating |

### 12.9 Other Functions

| Function | Method | Auth | Purpose |
|----------|--------|------|---------|
| `videosdk-token` | POST | Both | Generate video call token |
| `update-patient-demographics` | POST | Patient | Sync demographics |
| `get-security-questions` | GET | Public | Get list of 5 question keys |
| `lift-expired-suspensions` | CRON | Internal | Auto-lift doctor suspensions |
| `handle-missed-appointments` | CRON | Internal | Mark missed appointments |
| `appointment-reminder` | CRON | Internal | Send reminders 1h before |
| `log-performance-metrics` | POST | Public | Collect app performance data |

### 12.10 Shared Backend Utilities
- **auth.ts** - Patient/Doctor JWT validation, X-Patient-Token handling
- **bcrypt.ts** - PBKDF2-SHA256 (100K iterations) for token hashing
- **cors.ts** - CORS with allowed origins
- **errors.ts** - Standardized error responses
- **logger.ts** - Audit logging to admin_logs table
- **payment.ts** - Mock/production M-Pesa abstraction (Selcom API)
- **rateLimit.ts** - Upstash Redis sliding window rate limiting
- **resend.ts** - Email via Resend API
- **supabase.ts** - Service role client (bypasses RLS)

---

## 13. Push Notifications (FCM)

### iOS Equivalent: APNs (Apple Push Notification service)
The backend already supports FCM. For iOS:
- Use Firebase Cloud Messaging with APNs certificates
- OR switch to direct APNs with the existing `send-push-notification` edge function

### Notification Types & Handling
```
VIDEO_CALL_INCOMING → Data-only message (triggers even when backgrounded)
CONSULTATION_REQUEST → Incoming request notification
CONSULTATION_ACCEPTED → Navigate to chat
MESSAGE_RECEIVED → Show message preview
PAYMENT_STATUS → Update payment screen
REPORT_READY → Navigate to reports
APPOINTMENT_CONFIRMED/REMINDER/MISSED/RESCHEDULED/CANCELLED → Update appointments
DOCTOR_APPROVED/REJECTED/WARNED/SUSPENDED/UNSUSPENDED/BANNED/UNBANNED → Account status
```

### FCM Message Routing
On Android, `EsiriplusFirebaseMessagingService` routes messages via `FcmMessageRouter`:
- Medical privacy: FCM payload contains NO patient data
- Only notification_id, type, user_id/session_id, consultation_id, room_id, call_type
- Full data fetched from Supabase after receiving push

---

## 14. Background Workers

### Android Workers (map to iOS Background Tasks)
```
MessageSyncWorker     → Sync unsynced messages            → iOS: BGAppRefreshTask
PaymentSyncWorker     → Sync payment status               → iOS: BGAppRefreshTask
NotificationCleanup   → Delete old notifications           → iOS: BGProcessingTask
SyncScheduler         → Schedule periodic background sync  → iOS: BGTaskScheduler
```

### Services
```
DoctorOnlineService       → Keep doctor online status      → iOS: Background location or VoIP push
CallForegroundService     → Active call indicator          → iOS: CallKit
```

---

## 15. Admin Panel Integration Points

The admin panel is a separate Next.js web app but shares the same Supabase backend.

### Key Admin Actions That Affect Mobile App
1. **Doctor Verification:** Only verified doctors appear in mobile app's doctor list
2. **Doctor Suspension/Ban:** Affects login flow, shows status screens
3. **Doctor Warning:** Must be acknowledged before accessing dashboard
4. **Rating Flagging:** Flagged ratings may be hidden
5. **User Role Management:** Admin, HR, Finance, Audit roles
6. **Risk Scanning:** Automated fraud detection

### Admin Edge Functions
```
admin-portal-action: approve_doctor, reject_doctor, suspend_user, unsuspend_user,
                     delete_user_role, create_portal_user_with_password,
                     deauthorize_device, toggle_rating_flag, scan_for_risks,
                     check_admin_exists, setup_initial_admin, get_my_roles
```

---

## 16. Security Architecture

### 16.1 Token Security
- **Patient tokens:** Custom HS256 JWT, 24-hour expiry, 7-day refresh window
- **Doctor tokens:** Supabase Auth JWT, standard refresh
- **iOS:** Store tokens in Keychain (SecItemAdd/SecItemCopyMatching)

### 16.2 Database Encryption
- Android: SQLCipher with AES-256-GCM key from Android Keystore
- **iOS equivalent:** SQLCipher with key from iOS Keychain, or CoreData with FileProtection

### 16.3 Network Security
- Certificate pinning for Supabase domain
- **iOS:** Use URLSession with certificate pinning delegate

### 16.4 Backend Security
- PBKDF2-SHA256 (100K iterations) for token/answer hashing
- Rate limiting (Upstash Redis sliding window)
- Brute force protection (10 attempts per 30 min for recovery)
- Device binding for doctors
- RLS policies on all Supabase tables
- Audit logging of all sensitive actions
- M-Pesa callback IP allowlisting in production

### 16.5 App Security
- Biometric authentication (Face ID/Touch ID for iOS)
- App lock screen
- Backup exclusion for encrypted data

---

## 17. UI/UX Design System

### 17.1 Colors
```
BrandTeal:      #2A9D8F     // Primary action, headers, highlights
DarkText:       #000000     // ALL body text (never gray for readability)
SubtitleGray:   #000000     // Also black per design decision
SuccessGreen:   #16A34A     // Completed, accepted states
WarningOrange:  #EA580C     // Pending, other states
ErrorRed:       #DC2626     // Errors, bans, rejections
WarningAmber:   #F59E0B     // Suspension, warnings
CardBorder:     #E5E7EB     // Dividers, borders
SectionBg:      #F9FAFB     // Light gray form sections
```

### 17.2 Layout Patterns
- **Sticky bottom buttons** - Overlays content, stays visible during scroll
- **Tab bars** with indicator for multi-section content
- **Linear progress indicators** for multi-step flows
- **Cards** with RoundedCornerShape (12-16dp), 1-2dp stroke borders
- **Filter chips** for single/multi-selection
- **Dropdown menus** for select options
- **Bottom sheets** for modals (ratings, reports)
- **FABs** with pulsing animation for active state

### 17.3 Typography
- App name with Swahili tagline
- Monospace for Patient IDs
- Bold for headings, regular for body

### 17.4 Icons
- 20-56dp sizes, inline or in colored circles
- Status icons: checkmark (success), X (error), pause (suspended), warning (alert)

### 17.5 Animations
- Fade-in for splash screen (3s)
- Pulsing FAB for active consultation
- Countdown timers (60s for requests, consultation timer)
- Loading spinners for async operations

---

## 18. Localization

### Supported Languages (Fully)
- Arabic (ar), English (en), Spanish (es), French (fr), Hindi (hi), Swahili (sw)

### Coming Soon (31 more)
af, am, bn, zh, nl, fi, de, el, ha, he, ig, id, it, ja, ko, ms, no, pl, pt, ru, sv, tl, th, tr, uk, ur, vi, yo, zu

### Implementation
- Per-app language selection (not system-wide)
- Language picker screen during onboarding
- Stored in user preferences
- Context-aware localization for services/broadcasts

---

## 19. Third-Party Integrations

| Service | Purpose | Android SDK | iOS Equivalent |
|---------|---------|-------------|----------------|
| Supabase | Auth, DB, Realtime, Storage, Edge Functions | supabase-kt | supabase-swift |
| VideoSDK | Video/audio calls | videosdk-rtc | VideoSDK iOS SDK |
| Firebase | FCM, Analytics, Crashlytics | Firebase Android | Firebase iOS |
| M-Pesa/Selcom | Mobile payments | Backend only | Backend only (same) |
| Resend | Transactional email | Backend only | Backend only (same) |
| Upstash Redis | Rate limiting | Backend only | Backend only (same) |

---

## 20. Environment Variables & Configuration

### App-Level Config (BuildConfig on Android → Info.plist / .xcconfig on iOS)
```
SUPABASE_URL          // Supabase project URL
SUPABASE_ANON_KEY     // Supabase anonymous key (public)
VIDEO_SDK_TOKEN       // VideoSDK API token (if needed client-side)
```

### Backend Environment Variables (No change needed for iOS)
```
Authentication:
  JWT_SECRET / SUPABASE_JWT_SECRET     // HS256 key for patient JWTs
  SUPABASE_URL, SUPABASE_SERVICE_ROLE_KEY

Payment:
  PAYMENT_ENV (mock/sandbox/production)
  MPESA_CONSUMER_KEY, MPESA_CONSUMER_SECRET, MPESA_SHORTCODE, MPESA_PASSKEY
  MPESA_CALLBACK_URL
  SELCOM_API_URL, SELCOM_API_KEY, SELCOM_API_SECRET, SELCOM_VENDOR_ID

Notifications:
  FCM_PROJECT_ID, FCM_CLIENT_EMAIL, FCM_PRIVATE_KEY
  RESEND_API_KEY
  INTERNAL_SERVICE_KEY

Rate Limiting:
  UPSTASH_REDIS_REST_URL, UPSTASH_REDIS_REST_TOKEN

CORS:
  ALLOWED_ORIGIN (add iOS app origin if needed)
```

### App Constants
```
SESSION_EXPIRY_HOURS = 24
CONSULTATION_FEE_KES = 500 (in cents)
MAX_RETRY_ATTEMPTS = 3
PAYMENT_POLL_INTERVAL_MS = 3000
VIDEO_CALL_TIMEOUT_MS = 30_000
POLL_FAST_MS = 3000 (Realtime disconnected)
POLL_SLOW_MS = 30000 (Realtime connected)
TYPING_THROTTLE_MS = 2000
TYPING_AUTO_CLEAR_MS = 3000
TYPING_DISPLAY_TIMEOUT_MS = 5000
REQUEST_TTL_SECONDS = 60
```

---

## 21. iOS-Specific Recommendations

### 21.1 Architecture Recommendations
- **SwiftUI** for UI (equivalent to Jetpack Compose)
- **Swift Concurrency** (async/await) for coroutines equivalent
- **Combine/@Published** for StateFlow equivalent
- **Swift Package Manager** for dependency management
- **The Composable Architecture (TCA)** or plain MVVM with ObservableObject

### 21.2 Key iOS Equivalents
| Android | iOS |
|---------|-----|
| Hilt (DI) | Swinject, or manual DI container |
| Room (DB) | CoreData/SwiftData + SQLCipher, or GRDB |
| EncryptedSharedPreferences | Keychain Services |
| WorkManager | BGTaskScheduler |
| Foreground Service | Background Modes + CallKit |
| Jetpack Navigation | NavigationStack/NavigationPath |
| StateFlow | @Published + Combine |
| Flow | AsyncSequence or Combine Publisher |
| Retrofit | URLSession with async/await |
| OkHttp Interceptors | URLProtocol or URLSession delegate |
| Firebase Messaging | APNs + Firebase iOS SDK |
| Biometric (Android) | LocalAuthentication (Face ID/Touch ID) |
| Android Keystore | Keychain + Secure Enclave |
| DataStore | UserDefaults (non-sensitive) + Keychain (sensitive) |
| FileProvider | UIActivityViewController |
| Geocoder | CLGeocoder |
| PdfDocument | PDFKit (PDFDocument, PDFPage) |

### 21.3 iOS-Specific Features to Add
- **CallKit** for incoming call UI (replaces Android's foreground service)
- **PushKit/VoIP** for incoming call notifications (wake app for calls)
- **WidgetKit** for home screen widgets (optional)
- **Siri Shortcuts** for quick actions (optional)
- **App Clips** for instant access (optional)
- **HealthKit** integration for vital signs (optional future)

### 21.4 Permissions (iOS Info.plist)
```
NSCameraUsageDescription           // Camera for photos/video calls
NSMicrophoneUsageDescription       // Microphone for audio/video calls
NSLocationWhenInUseUsageDescription // Location for region detection
NSPhotoLibraryUsageDescription     // Photo library for attachments
NSFaceIDUsageDescription           // Face ID for biometric auth
```

### 21.5 Build Configuration
- **Min iOS:** 16.0 (for NavigationStack, modern SwiftUI)
- **Target:** iOS 17.0+ recommended
- **Supported devices:** iPhone only (or Universal)
- **Orientations:** Portrait only (matching Android)

---

## Appendix A: Complete Edge Function Request/Response Examples

### A.1 Create Patient Session
```
POST /functions/v1/create-patient-session
Headers: Authorization: Bearer {SUPABASE_ANON_KEY}
Body: {} (empty or {legacy_patient_id: "..."})

Response 201:
{
  "session_id": "uuid-...",
  "patient_id": "ESR-A2B3-C4D5",
  "access_token": "eyJhbGci...",
  "refresh_token": "base64...",
  "expires_at": "2026-03-17T10:00:00Z"
}
```

### A.2 Handle Consultation Request (Create)
```
POST /functions/v1/handle-consultation-request
Headers: Authorization: Bearer {SUPABASE_ANON_KEY}, X-Patient-Token: {patient_jwt}
Body: {
  "action": "create",
  "doctor_id": "uuid-...",
  "service_type": "gp",
  "consultation_type": "video",
  "chief_complaint": "Persistent cough for 3 days",
  "patient_age_group": "18-30",
  "patient_sex": "M",
  "patient_blood_group": "O+",
  "patient_allergies": "Penicillin",
  "patient_chronic_conditions": ""
}

Response 200:
{
  "request_id": "uuid-...",
  "status": "pending",
  "created_at": "2026-03-16T10:00:00Z",
  "expires_at": "2026-03-16T10:01:00Z",
  "ttl_seconds": 60
}
```

### A.3 M-Pesa STK Push
```
POST /functions/v1/mpesa-stk-push
Headers: Authorization: Bearer {token}
Body: {
  "phone_number": "255712345678",
  "amount": 10000,
  "payment_type": "service_access",
  "service_type": "gp",
  "idempotency_key": "uuid-1234-5678"
}

Response 200:
{
  "message": "STK push sent successfully",
  "payment_id": "uuid-...",
  "checkout_request_id": "ws_...",
  "payment_env": "mock",
  "status": "initiated"
}
```

### A.4 Send Message
```
POST /functions/v1/handle-messages
Headers: Authorization: Bearer {token} (or X-Patient-Token)
Body: {
  "action": "send",
  "message_id": "uuid-...",
  "consultation_id": "uuid-...",
  "sender_type": "patient",
  "sender_id": "session-id-...",
  "message_text": "I have been experiencing...",
  "message_type": "text"
}

Response 200:
{
  "message_id": "uuid-...",
  "consultation_id": "uuid-...",
  "sender_type": "patient",
  "message_text": "...",
  "is_read": false,
  "created_at": "2026-03-16T10:05:00Z"
}
```

---

## Appendix B: Validation Rules

```
Email:              ^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$
Password:           Min 8 chars, 1+ uppercase, 1+ digit
Phone:              7-15 digits
Tanzanian Phone:    255[67]XXXXXXXX
Full Name:          2-100 characters
Bio:                10-1000 characters
Years Experience:   0-70 range
Chief Complaint:    10-1000 characters
OTP Code:           Exactly 6 digits
Payment Amount:     > 0
Recharge Minutes:   10, 30, 60, or 120
License Number:     Required (non-blank)
Service Type:       nurse, clinical_officer, pharmacist, gp, specialist, psychologist
Consultation Type:  chat, video, both
Idempotency Key:    Min 8 characters
```

---

## Appendix C: Rate Limits (Backend)

```
payment:       10/min
read:          30/min
notification:  20/min
sensitive:      5/min
message:       60/min
video:         20/min
consultation:  15/min
```

---

*Document generated from Android codebase analysis on 2026-03-16*
*For questions, refer to the Android source code at D:\esiriplus\*
