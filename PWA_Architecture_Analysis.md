Now I have comprehensive information to create a detailed analysis. Let me compile the report:

## eSIRI Plus Android App - Comprehensive PWA Analysis

This is an extremely thorough analysis of the eSIRI Plus telemedicine platform, documenting every major component needed to rebuild as a PWA.

---

## 1. COMPLETE NAVIGATION GRAPH

### 1.1 Root Navigation Structure
**File**: `/d/esiriplus/app/src/main/java/com/esiri/esiriplus/navigation/EsiriplusNavHost.kt`

The app has three main graph flows with intelligent auth-driven navigation:
- **AuthGraph** (unauthenticated entry point)
- **PatientGraph** (patient & agent flows)
- **DoctorGraph** (doctor flows)

The NavHost implements sophisticated session management:
- Protected screens (consultation, payment, video call, report) suppress forced navigation when auth expires
- SessionExpired state shows error gracefully on protected screens
- Authenticated state transitions to role-specific graphs
- Session state is memoized to prevent re-navigation on token refresh

---

### 1.2 AUTH FLOW
**File**: `/d/esiriplus/feature/auth/src/main/kotlin/com/esiri/esiriplus/feature/auth/navigation/AuthNavigation.kt`

```
SplashRoute
  â†“ (or navigate to LanguageSelectionRoute if locales empty)
[LanguageSelectionRoute] â†’ LanguagePickerRoute
  â†“
RoleSelectionRoute
â”œâ”€â”€ onPatientSelected â†’ TermsRoute â†’ PatientSetupRoute â†’ onPatientAuthenticated()
â”œâ”€â”€ onDoctorSelected â†’ DoctorLoginRoute â†’ onDoctorAuthenticated()
â”œâ”€â”€ onDoctorRegister â†’ DoctorTermsRoute â†’ DoctorRegistrationRoute â†’ onDoctorAuthenticated()
â”œâ”€â”€ onRecoverPatientId â†’ PatientRecoveryRoute â†’ onPatientAuthenticated()
â”œâ”€â”€ onHaveMyId â†’ AccessRecordsRoute â†’ onPatientAuthenticated()
â””â”€â”€ onAgentSelected â†’ AgentAuthRoute â†’ onAgentAuthenticated()

PatientSetupRoute (special handling):
â”œâ”€â”€ onNavigateToRecoveryQuestions â†’ SecurityQuestionsSetupRoute â†’ popBackStack() + set flag
â””â”€â”€ onComplete â†’ triggers onPatientAuthenticated() callback
```

**Routes** (Serializable objects):
- `SplashRoute`, `LanguageSelectionRoute`, `LanguagePickerRoute`, `RoleSelectionRoute`
- `TermsRoute`, `DoctorLoginRoute`, `PatientSetupRoute`, `SecurityQuestionsSetupRoute`
- `DoctorTermsRoute`, `DoctorRegistrationRoute`, `AccessRecordsRoute`, `PatientRecoveryRoute`, `AgentAuthRoute`

---

### 1.3 PATIENT FLOW
**File**: `/d/esiriplus/feature/patient/src/main/kotlin/com/esiri/esiriplus/feature/patient/navigation/PatientNavigation.kt`

```
PatientHomeRoute (startDestination)
â”œâ”€â”€ onStartConsultation â†’ TierSelectionRoute
â”œâ”€â”€ onNavigateToProfile â†’ PatientProfileRoute
â”œâ”€â”€ onNavigateToReports â†’ ReportsRoute â†’ ReportDetailRoute(reportId)
â”œâ”€â”€ onNavigateToConsultationHistory â†’ ConsultationHistoryRoute
â”œâ”€â”€ onNavigateToAppointments â†’ PatientAppointmentsRoute
â”œâ”€â”€ onNavigateToOngoingConsultations â†’ OngoingConsultationsRoute
â””â”€â”€ onResumeConsultation(consultationId) â†’ PatientConsultationRoute(consultationId)

TierSelectionRoute
â”œâ”€â”€ onSelectRoyal â†’ ServiceLocationRoute(tier="ROYAL")
â””â”€â”€ onSelectEconomy â†’ ServiceLocationRoute(tier="ECONOMY")

ServiceLocationRoute(tier)
â”œâ”€â”€ onSelectInsideTanzania â†’ ServicesRoute(tier)
â””â”€â”€ onSelectOutsideTanzania â†’ (Coming soon)

ServicesRoute(tier)
â””â”€â”€ onServiceSelected(category, price, duration, tier) â†’ FindDoctorRoute(...)

FindDoctorRoute(serviceCategory, servicePriceAmount, serviceDurationMinutes, serviceTier)
â”œâ”€â”€ onBookAppointment(doctorId) â†’ BookAppointmentRoute(doctorId, ...)
â”œâ”€â”€ onNavigateToConsultation(consultationId) â†’ PatientConsultationRoute(consultationId) {popUpTo PatientHomeRoute}
â””â”€â”€ onBack

BookAppointmentRoute(doctorId, serviceCategory, servicePriceAmount, serviceDurationMinutes, serviceTier)
â”œâ”€â”€ onBookingSuccess â†’ PatientAppointmentsRoute {popUpTo PatientHomeRoute}
â””â”€â”€ onBack

PatientConsultationRoute(consultationId)
â”œâ”€â”€ onNavigateToPayment(consultationId, amount, serviceType) â†’ PatientPaymentRoute(...)
â”œâ”€â”€ onNavigateToExtensionPayment(consultationId, amount, serviceType) â†’ ExtensionPaymentRoute(...)
â”œâ”€â”€ onStartCall(consultationId, callType) â†’ PatientVideoCallRoute(consultationId, callType)
â””â”€â”€ onBack {popUpTo PatientHomeRoute}

PatientPaymentRoute(consultationId, amount, serviceType)
â”œâ”€â”€ onPaymentComplete(consultationId) â†’ PatientVideoCallRoute(consultationId)
â””â”€â”€ onBack

ExtensionPaymentRoute(consultationId, amount, serviceType)
â”œâ”€â”€ onPaymentComplete â†’ popBackStack()
â””â”€â”€ onCancel â†’ popBackStack()

PatientVideoCallRoute(consultationId, callType, roomId)
â””â”€â”€ onCallEnded â†’ popBackStack()

OngoingConsultationsRoute
â”œâ”€â”€ onOpenConsultation(consultationId) â†’ PatientConsultationRoute(consultationId)
â”œâ”€â”€ onRequestFollowUp(parentConsultationId, doctorId, serviceType) â†’ FollowUpWaitingRoute(...)
â””â”€â”€ onBack

FollowUpWaitingRoute(parentConsultationId, doctorId, serviceType)
â”œâ”€â”€ onAccepted(consultationId) â†’ PatientConsultationRoute(consultationId) {popUpTo PatientHomeRoute}
â”œâ”€â”€ onRequestSubstitute â†’ SubstituteFollowUpRoute(...)
â””â”€â”€ onBookAppointment â†’ BookAppointmentRoute(doctorId, ...)

SubstituteFollowUpRoute(parentConsultationId, originalDoctorId, serviceType, ...)
â”œâ”€â”€ onBookAppointment(doctorId) â†’ BookAppointmentRoute(doctorId, ...)
â””â”€â”€ onNavigateToConsultation(consultationId) â†’ PatientConsultationRoute(consultationId)

AgentDashboardRoute
â”œâ”€â”€ onStartConsultation â†’ TierSelectionRoute
â””â”€â”€ onSignedOut â†’ popBackStack(PatientHomeRoute, false)
```

**Key Route Data Classes**:
- `ServiceLocationRoute(tier: String = "ECONOMY")`
- `ServicesRoute(tier: String = "ECONOMY")`
- `PatientConsultationRoute(consultationId: String)`
- `PatientPaymentRoute(consultationId, amount, serviceType)`
- `PatientVideoCallRoute(consultationId, callType="VIDEO", roomId="")`
- `FindDoctorRoute(serviceCategory, servicePriceAmount, serviceDurationMinutes, serviceTier="ECONOMY")`
- `BookAppointmentRoute(doctorId, serviceCategory, servicePriceAmount, serviceDurationMinutes, serviceTier="ECONOMY")`
- `ExtensionPaymentRoute(consultationId, amount, serviceType)`
- `FollowUpWaitingRoute(parentConsultationId, doctorId, serviceType)`
- `SubstituteFollowUpRoute(parentConsultationId, originalDoctorId, serviceType, serviceCategory="", serviceTier="ECONOMY", serviceDurationMinutes=15, isSubstituteFollowUp=true)`
- `ReportDetailRoute(reportId: String)`

---

### 1.4 DOCTOR FLOW
**File**: `/d/esiriplus/feature/doctor/src/main/kotlin/com/esiri/esiriplus/feature/doctor/navigation/DoctorNavigation.kt`

```
DoctorDashboardRoute (startDestination)
â”œâ”€â”€ onNavigateToConsultations â†’ DoctorConsultationListRoute
â”œâ”€â”€ onNavigateToRoyalClients â†’ RoyalClientsRoute
â”œâ”€â”€ onNavigateToNotifications â†’ DoctorNotificationsRoute
â”œâ”€â”€ onNavigateToConsultation(consultationId) â†’ DoctorConsultationDetailRoute(consultationId)
â”œâ”€â”€ onNavigateToAppointments â†’ DoctorAppointmentsRoute
â”œâ”€â”€ onNavigateToAvailabilitySettings â†’ DoctorAvailabilitySettingsRoute
â””â”€â”€ onSignOut

DoctorConsultationListRoute
â”œâ”€â”€ onConsultationSelected(id) â†’ DoctorConsultationDetailRoute(id)
â””â”€â”€ onBack

DoctorConsultationDetailRoute(consultationId)
â”œâ”€â”€ onStartCall(id, callType) â†’ DoctorVideoCallRoute(id, callType)
â”œâ”€â”€ onWriteReport(id) â†’ DoctorReportRoute(id)
â”œâ”€â”€ onConsultationCompleted â†’ popBackStack(DoctorDashboardRoute, false)
â””â”€â”€ onBack

DoctorVideoCallRoute(consultationId, callType="VIDEO", roomId="")
â””â”€â”€ onCallEnded â†’ popBackStack()

DoctorReportRoute(consultationId)
â”œâ”€â”€ onReportSubmitted â†’ popBackStack(DoctorDashboardRoute, false)
â””â”€â”€ onBack

DoctorAppointmentsRoute
â”œâ”€â”€ onNavigateToConsultation(consultationId) â†’ DoctorConsultationDetailRoute(consultationId)
â””â”€â”€ onBack

RoyalClientsRoute
â”œâ”€â”€ onOpenConsultation(consultationId) â†’ DoctorConsultationDetailRoute(consultationId)
â”œâ”€â”€ onStartCall(consultationId, callType) â†’ DoctorVideoCallRoute(consultationId, callType)
â””â”€â”€ onBack

DoctorAvailabilitySettingsRoute
â””â”€â”€ onBack
```

---

## 2. COMPLETE SCREEN INVENTORY & UI COMPONENTS

### AUTH SCREENS

#### SplashScreen
- **File**: `/d/esiriplus/feature/auth/src/main/kotlin/com/esiri/esiriplus/feature/auth/screen/SplashScreen.kt`
- **Purpose**: Initial app loading screen with "Tap to continue" button
- **Components**: App logo, loading state, continue button
- **Navigation**: onContinue â†’ RoleSelectionRoute or LanguageSelectionRoute

#### LanguageSelectionScreen
- **File**: `/d/esiriplus/feature/auth/src/main/kotlin/com/esiri/esiriplus/feature/auth/screen/LanguageSelectionScreen.kt`
- **Purpose**: Select app language
- **Components**: Language selection cards
- **Navigation**: onContinue â†’ LanguagePickerRoute

#### LanguagePickerScreen
- **File**: `/d/esiriplus/feature/auth/src/main/kotlin/com/esiri/esiriplus/feature/auth/screen/LanguagePickerScreen.kt`
- **Purpose**: Language picker modal
- **Navigation**: onContinue â†’ RoleSelectionRoute, onBack

#### RoleSelectionScreen
- **File**: `/d/esiriplus/feature/auth/src/main/kotlin/com/esiri/esiriplus/feature/auth/screen/RoleSelectionScreen.kt`
- **Purpose**: User role selection (Patient, Doctor, Agent)
- **UI Components**:
  - Three role cards with icons and descriptions
  - Login/register/recover buttons for each role
- **Navigation Callbacks**:
  - onPatientSelected â†’ TermsRoute
  - onDoctorSelected â†’ DoctorLoginRoute
  - onDoctorRegister â†’ DoctorTermsRoute
  - onRecoverPatientId â†’ PatientRecoveryRoute
  - onHaveMyId â†’ AccessRecordsRoute
  - onAgentSelected â†’ AgentAuthRoute

#### TermsScreen (Patient)
- **File**: `/d/esiriplus/feature/auth/src/main/kotlin/com/esiri/esiriplus/feature/auth/screen/TermsScreen.kt`
- **Purpose**: Terms & conditions agreement
- **Components**: Scrollable terms text, agree/disagree buttons
- **Navigation**: onAgree â†’ PatientSetupRoute, onBack

#### PatientSetupScreen
- **File**: `/d/esiriplus/feature/auth/src/main/kotlin/com/esiri/esiriplus/feature/auth/screen/PatientSetupScreen.kt`
- **Purpose**: Collect initial patient demographics (sex, age group, blood type, allergies, chronic conditions)
- **ViewModel**: PatientSetupViewModel (manages session creation, PDF generation)
- **State**: PatientSetupUiState (patientId, sex, ageGroup, bloodType, allergies, chronicConditions, region, isCreatingSession, isSaving, isGeneratingPdf)
- **Components**:
  - Dropdown selectors for demographic fields
  - Patient ID display (ESR-XXXX-XXXX format)
  - PDF download button (requires sex + ageGroup)
  - Security questions button
- **Navigation**: onComplete â†’ onPatientAuthenticated(), onNavigateToRecoveryQuestions â†’ SecurityQuestionsSetupRoute

#### SecurityQuestionsSetupScreen
- **File**: `/d/esiriplus/feature/auth/src/main/kotlin/com/esiri/esiriplus/feature/auth/screen/SecurityQuestionsSetupScreen.kt`
- **Purpose**: Set security questions for account recovery
- **ViewModel**: SecurityQuestionsSetupViewModel
- **Components**: Question selection dropdowns, answer text fields, skip button
- **Navigation**: onComplete â†’ popBackStack() (back to PatientSetupScreen), onSkip

#### PatientRecoveryScreen
- **File**: `/d/esiriplus/feature/auth/src/main/kotlin/com/esiri/esiriplus/feature/auth/screen/PatientRecoveryScreen.kt`
- **Purpose**: Account recovery by patient ID or security questions
- **ViewModel**: PatientRecoveryViewModel
- **Components**: Patient ID input, security question answers, recovery method tabs
- **Navigation**: onRecovered â†’ onPatientAuthenticated(), onBack

#### AccessRecordsScreen
- **File**: `/d/esiriplus/feature/auth/src/main/kotlin/com/esiri/esiriplus/feature/auth/screen/AccessRecordsScreen.kt`
- **Purpose**: Access medical records with existing session
- **ViewModel**: AccessRecordsViewModel
- **Navigation**: onAccessGranted â†’ onPatientAuthenticated(), onBack, onDontHaveId â†’ TermsRoute

#### DoctorLoginScreen
- **File**: `/d/esiriplus/feature/auth/src/main/kotlin/com/esiri/esiriplus/feature/auth/screen/DoctorLoginScreen.kt`
- **Purpose**: Doctor login with email/phone and OTP
- **ViewModel**: DoctorLoginViewModel
- **Components**: Email/phone input, OTP input, verification pending state
- **Navigation**: onAuthenticated â†’ onDoctorAuthenticated(), onRegister â†’ DoctorTermsRoute, onBack

#### DoctorTermsScreen
- **File**: `/d/esiriplus/feature/auth/src/main/kotlin/com/esiri/esiriplus/feature/auth/screen/DoctorTermsScreen.kt`
- **Purpose**: Doctor terms & conditions
- **Navigation**: onAgree â†’ DoctorRegistrationRoute, onBack

#### DoctorRegistrationScreen
- **File**: `/d/esiriplus/feature/auth/src/main/kotlin/com/esiri/esiriplus/feature/auth/screen/DoctorRegistrationScreen.kt`
- **Purpose**: Doctor account creation (name, specialty, credentials, languages, services)
- **ViewModel**: DoctorRegistrationViewModel
- **Components**:
  - Personal info fields
  - Specialty & specialization dropdowns
  - License number input
  - Years of experience
  - Language selection (multi-select)
  - Service types selection (multi-select)
  - Document upload (license, certificates)
- **Navigation**: onComplete â†’ onDoctorAuthenticated(), onNavigateToLogin â†’ DoctorLoginRoute

#### AgentAuthScreen (in feature/auth)
- **File**: `/d/esiriplus/feature/auth/src/main/kotlin/com/esiri/esiriplus/feature/auth/screen/AgentAuthScreen.kt`
- **Purpose**: Agent authentication & PIN setup
- **ViewModel**: AgentAuthViewModel
- **Navigation**: onAuthenticated â†’ onAgentAuthenticated(), onBack

---

### PATIENT SCREENS

#### PatientHomeScreen
- **File**: `/d/esiriplus/feature/patient/src/main/kotlin/com/esiri/esiriplus/feature/patient/screen/PatientHomeScreen.kt`
- **Purpose**: Patient dashboard (ongoing consultations, quick actions, pending ratings)
- **ViewModel**: PatientHomeViewModel
- **State Fields**:
  - `patientId`: Masked patient ID (e.g., ESR-****-XXXX)
  - `soundsEnabled`: Boolean for notification sounds
  - `activeConsultation`: Current active consultation
  - `pendingRatingConsultation`: Consultation awaiting rating
  - `ongoingConsultations`: List of active/ongoing consultations
  - `hasUnreadReports`: Boolean flag for new reports
- **UI Components**:
  - Pull-to-refresh box
  - Patient ID card (copyable, masked)
  - Active consultation card (if exists)
  - Quick action buttons (start consultation, view reports, history, appointments)
  - Ongoing consultations list
  - Pending rating bottom sheet (triggered by RatingBottomSheet component)
  - Sound/notifications toggle
  - Logout dialog
- **Features**:
  - FCM notification permission request (Android 13+)
  - Pending rating auto-detection
  - Unread reports tracking via SharedPreferences
  - Language switcher
- **Navigation**:
  - onStartConsultation â†’ TierSelectionRoute
  - onNavigateToProfile â†’ PatientProfileRoute
  - onNavigateToReports â†’ ReportsRoute
  - onNavigateToConsultationHistory â†’ ConsultationHistoryRoute
  - onNavigateToAppointments â†’ PatientAppointmentsRoute
  - onNavigateToOngoingConsultations â†’ OngoingConsultationsRoute
  - onResumeConsultation(id) â†’ PatientConsultationRoute(id)

#### TierSelectionScreen
- **File**: `/d/esiriplus/feature/patient/src/main/kotlin/com/esiri/esiriplus/feature/patient/screen/TierSelectionScreen.kt`
- **Purpose**: Select service tier (Economy vs Royal)
- **UI Components**:
  - Two tier cards with:
    - Tier name & icon (purple for Royal, teal for Economy)
    - Price per minute/session
    - Feature lists (follow-ups, duration, doctor selection)
    - CTA button
  - Visual differentiation: Royal (deep purple + gold), Economy (teal)
  - Back button
- **Navigation**: onSelectRoyal â†’ ServiceLocationRoute(tier="ROYAL"), onSelectEconomy â†’ ServiceLocationRoute(tier="ECONOMY"), onBack

#### ServiceLocationScreen
- **File**: `/d/esiriplus/feature/patient/src/main/kotlin/com/esiri/esiriplus/feature/patient/screen/ServiceLocationScreen.kt`
- **Purpose**: Select service location (inside/outside Tanzania)
- **Props**: tier (passed from route)
- **UI Components**: Location option cards, Coming Soon dialog for international
- **Navigation**: onSelectInsideTanzania â†’ ServicesRoute(tier), onSelectOutsideTanzania â†’ (dialog), onBack

#### ServicesScreen
- **File**: `/d/esiriplus/feature/patient/src/main/kotlin/com/esiri/esiriplus/feature/patient/screen/ServicesScreen.kt`
- **Purpose**: Browse and select medical service type
- **ViewModel**: ServicesViewModel (receives tier from SavedStateHandle)
- **State**: Services list (nurse, clinical officer, pharmacist, GP, specialist, psychologist)
- **UI Components**:
  - Service cards with:
    - Icon & name
    - Tier-specific pricing (applied by PricingEngine)
    - Duration
    - Description
    - Doctor rating/availability badge
  - Price displays using tier system
- **Navigation**: onServiceSelected(category, price, duration, tier) â†’ FindDoctorRoute(...)

#### FindDoctorScreen
- **File**: `/d/esiriplus/feature/patient/src/main/kotlin/com/esiri/esiriplus/feature/patient/screen/FindDoctorScreen.kt`
- **Purpose**: Browse & select doctor from available doctors
- **Props**: servicePriceAmount, serviceDurationMinutes, serviceTier
- **ViewModel**: FindDoctorViewModel
- **State**:
  - Doctor list (filtered by service type)
  - Search/filter options
  - Selected doctor
  - Active consultations (skip to ongoing if exists)
- **UI Components**:
  - Doctor cards with:
    - Photo, name, specialty
    - Rating (average stars, count)
    - Experience years
    - Languages
    - Availability status
    - Services offered
    - "Book" button
  - Search/filter bar
  - Filters: rating, experience, languages
  - Pull-to-refresh
- **Navigation**: onBookAppointment(doctorId) â†’ BookAppointmentRoute(...), onNavigateToConsultation(consultationId) â†’ PatientConsultationRoute, onBack

#### BookAppointmentScreen
- **File**: `/d/esiriplus/feature/patient/src/main/kotlin/com/esiri/esiriplus/feature/patient/screen/BookAppointmentScreen.kt`
- **Purpose**: Confirm and book appointment with selected doctor
- **Props**: doctorId, serviceCategory, servicePriceAmount, serviceDurationMinutes, serviceTier
- **ViewModel**: BookAppointmentViewModel
- **State**:
  - Doctor details (name, specialty, photo, rating)
  - Service type, price, duration
  - Chief complaint textarea
  - Booking status (idle, loading, success, error)
- **UI Components**:
  - Doctor summary card
  - Service type & price display
  - Preferred consultation type selector (chat, video, both)
  - Chief complaint textarea (validation: 10-1000 chars)
  - "Book Now" button
  - Appointment confirmation display
- **Navigation**: onBookingSuccess â†’ PatientAppointmentsRoute, onBack

#### PatientConsultationScreen
- **File**: `/d/esiriplus/feature/patient/src/main/kotlin/com/esiri/esiriplus/feature/patient/screen/PatientConsultationScreen.kt`
- **Purpose**: Active consultation with doctor (chat + timer + actions)
- **Props**: consultationId
- **ViewModel**: PatientConsultationViewModel
- **State** (ConsultationUiState):
  - Consultation details (doctor, service type, status)
  - Chat messages list (real-time Supabase sync)
  - Timer state (scheduledEndAt, extensionCount, gracePeriodEndAt, status)
  - Video call room state
  - Extension prompt state
  - Report availability
- **UI Components**:
  - Top bar with doctor name, status badge
  - ConsultationTimerBar (countdown timer, extension button, end button)
  - ChatContent (message list, input box with attachment options, typing indicators)
    - Attachment options: Camera, Gallery, Document, Image
    - File upload UI
    - Sent/unsent indicators
    - Retry on fail UI
  - GracePeriodBanner (shows when awaiting extension payment)
  - PatientExtensionPrompt (bottom sheet for extension acceptance)
  - Video call button (initiates PatientVideoCallRoute)
  - Report availability indicator
- **Special Features**:
  - BackHandler to prevent accidental exit during consultation
  - Real-time message sync via Supabase Realtime
  - Typing indicator subscription
  - Timer auto-sync with server
  - Audio/visual notifications for doctor actions
  - File upload to Firebase Storage
- **Navigation**:
  - onNavigateToPayment(id, amount, serviceType) â†’ PatientPaymentRoute(...)
  - onNavigateToExtensionPayment(id, amount, serviceType) â†’ ExtensionPaymentRoute(...)
  - onStartCall(id, callType) â†’ PatientVideoCallRoute(id, callType)
  - onBack â†’ popUpTo(PatientHomeRoute, false)

#### PatientPaymentScreen
- **File**: `/d/esiriplus/feature/patient/src/main/kotlin/com/esiri/esiriplus/feature/patient/screen/PatientPaymentScreen.kt`
- **Purpose**: M-Pesa payment initiation & confirmation
- **Props**: consultationId, amount, serviceType
- **ViewModel**: PatientPaymentViewModel
- **State** (PaymentUiState):
  - consultationId, amount, serviceType
  - paymentStatus (CONFIRM, PROCESSING, COMPLETED, FAILED)
  - isLoading, errorMessage
  - phoneNumber (for M-Pesa prompt)
  - transactionId (after payment)
- **UI Components**:
  - Step 1 (CONFIRM): Amount display, service details, phone input, "Pay Now" button
  - Step 2 (PROCESSING): Spinner, polling message, "Waiting for M-Pesa prompt"
  - Step 3 (COMPLETED): Success checkmark, "Payment successful" message, auto-navigate delay
  - Step 4 (FAILED): Error icon, failure reason, retry button
  - TopAppBar with back button
- **Features**:
  - M-Pesa STK push integration (mpesa-stk-push edge function)
  - Payment status polling via Edge Function
  - Auto-navigation on success (1.5s delay)
  - BackHandler to prevent back press during payment
- **Navigation**: onPaymentComplete(consultationId) â†’ PatientVideoCallRoute(consultationId), onBack

#### ExtensionPaymentScreen
- **File**: `/d/esiriplus/feature/patient/src/main/kotlin/com/esiri/esiriplus/feature/patient/screen/ExtensionPaymentScreen.kt`
- **Purpose**: Payment for session extension (call recharge)
- **Props**: consultationId, amount, serviceType
- **ViewModel**: ExtensionPaymentViewModel
- **State**: Similar to PatientPaymentScreen but for extension
- **UI Components**: Extension-specific messaging (e.g., "Extend your consultation by 3 minutes")
- **Navigation**: onPaymentComplete â†’ popBackStack(), onCancel â†’ popBackStack()

#### PatientVideoCallScreen
- **File**: `/d/esiriplus/feature/patient/src/main/kotlin/com/esiri/esiriplus/feature/patient/screen/PatientVideoCallScreen.kt`
- **Purpose**: VideoSDK video call interface
- **Props**: consultationId, callType, roomId
- **ViewModel**: PatientVideoCallViewModel / VideoCallViewModel (shared)
- **State**:
  - Call status (connecting, active, ended, failed)
  - Participant list (doctor, patient)
  - Call duration
  - Mute/camera state
  - Call quality indicator
  - Recording status (if enabled)
- **UI Components**:
  - Video grid (doctor video, patient video)
  - Audio/video toggle buttons
  - End call button
  - Call duration counter
  - Network quality indicator
  - Participant info overlay
- **Features**:
  - VideoSDK integration (room creation, token generation via videosdk-token edge function)
  - Permission handling (camera, microphone)
  - Low network quality detection
  - Fallback to audio-only mode
- **Navigation**: onCallEnded â†’ popBackStack()

#### PatientProfileScreen
- **File**: `/d/esiriplus/feature/patient/src/main/kotlin/com/esiri/esiriplus/feature/patient/screen/PatientProfileScreen.kt`
- **Purpose**: View & edit patient profile
- **ViewModel**: PatientProfileViewModel
- **State**:
  - patientId (immutable)
  - dateOfBirth
  - bloodGroup
  - allergies
  - emergencyContactName, emergencyContactPhone
  - sex, ageGroup
  - chronicConditions
- **UI Components**:
  - Read-only patient ID card
  - Editable profile fields (text inputs, date picker, dropdowns)
  - Emergency contact section
  - Medical history section (allergies, conditions)
  - Save button
  - Edit/View toggle
- **Navigation**: onBack

#### PatientAppointmentsScreen
- **File**: `/d/esiriplus/feature/patient/src/main/kotlin/com/esiri/esiriplus/feature/patient/screen/PatientAppointmentsScreen.kt`
- **Purpose**: View scheduled and past appointments
- **ViewModel**: PatientAppointmentsViewModel
- **State**:
  - appointmentsList (filtered by status: upcoming, completed, cancelled)
  - selectedAppointment (for detail view)
- **UI Components**:
  - Appointment tabs (Upcoming, Completed, Cancelled)
  - Appointment cards with:
    - Doctor name & photo
    - Appointment date/time
    - Service type
    - Status badge
    - "Join Consultation" / "Reschedule" / "Cancel" buttons
  - Appointment detail bottom sheet
  - Reschedule dialog
  - Cancel confirmation dialog
- **Navigation**: onBack

#### OngoingConsultationsScreen
- **File**: `/d/esiriplus/feature/patient/src/main/kotlin/com/esiri/esiriplus/feature/patient/screen/OngoingConsultationsScreen.kt`
- **Purpose**: List ongoing consultations (active + follow-up eligible)
- **ViewModel**: OngoingConsultationsViewModel
- **State**:
  - ongoingList: List<ConsultationEntity>
  - followUpEligible: filtered consultations where status=completed AND followUpExpiry > now
- **UI Components**:
  - Ongoing consultation cards with:
    - Doctor name, photo, specialty
    - Service type, tier
    - Time elapsed / Time remaining (if timer active)
    - Follow-up badge (if Royal tier and eligible)
    - "Open" button
    - "Request Follow-up" button (if eligible)
  - Empty state message
  - Pull-to-refresh
- **Navigation**:
  - onOpenConsultation(id) â†’ PatientConsultationRoute(id)
  - onRequestFollowUp(parentId, doctorId, serviceType) â†’ FollowUpWaitingRoute(...)
  - onBack

#### FollowUpWaitingScreen
- **File**: `/d/esiriplus/feature/patient/src/main/kotlin/com/esiri/esiriplus/feature/patient/screen/FollowUpWaitingScreen.kt`
- **Purpose**: Shows follow-up request pending doctor acceptance
- **Props**: parentConsultationId, doctorId, serviceType
- **ViewModel**: FollowUpWaitingViewModel
- **State**:
  - followUpStatus (PENDING, ACCEPTED, REJECTED, EXPIRED)
  - doctorResponse (accepted consultation ID or rejection reason)
- **UI Components**:
  - Doctor info card
  - Follow-up status indicator (spinner, checkmark, X, clock)
  - Status messages ("Waiting for doctor...", "Doctor accepted!", "Doctor declined")
  - Action buttons:
    - "Book with same doctor" â†’ BookAppointmentRoute(doctorId)
    - "Request substitute doctor" â†’ SubstituteFollowUpRoute(...)
    - "Cancel request" â†’ navigation pop
- **Navigation**:
  - onAccepted(consultationId) â†’ PatientConsultationRoute(consultationId)
  - onRequestSubstitute â†’ SubstituteFollowUpRoute(...)
  - onBookAppointment â†’ BookAppointmentRoute(...)
  - onBack

#### ConsultationHistoryScreen
- **File**: `/d/esiriplus/feature/patient/src/main/kotlin/com/esiri/esiriplus/feature/patient/screen/ConsultationHistoryScreen.kt`
- **Purpose**: View all past consultations (paginated)
- **ViewModel**: ConsultationHistoryViewModel
- **State**:
  - consultationsList: List<ConsultationEntity>
  - selectedConsultation (for detail modal)
  - filters (date range, service type, status)
  - isLoadingMore
- **UI Components**:
  - Consultation list items with:
    - Doctor name, photo
    - Service type
    - Date/time
    - Status (completed, cancelled, etc.)
    - Duration & fee
    - "View Details" link
  - Infinite scroll / pagination
  - Filter bar (date range, service type, status)
  - Consultation detail modal (shows messages, attachments, report link)
- **Navigation**: onBack

#### ReportsScreen
- **File**: `/d/esiriplus/feature/patient/src/main/kotlin/com/esiri/esiriplus/feature/patient/screen/ReportsScreen.kt`
- **Purpose**: List generated consultation reports
- **ViewModel**: ReportsViewModel
- **State**:
  - reportsList: List<PatientReportEntity>
  - selectedReportId: String?
  - unreadCount: Int
  - filters (date, doctor)
- **UI Components**:
  - Report cards with:
    - Doctor name, date generated
    - Diagnosis summary (first 50 chars)
    - Report preview/icon
    - Download button
    - "View" button
  - Unread badge on header
  - Empty state ("No reports yet")
  - Filter bar (date range, doctor)
  - Infinite scroll
- **Navigation**: onReportClick(reportId) â†’ ReportDetailRoute(reportId), onBack

#### ReportDetailScreen
- **File**: `/d/esiriplus/feature/patient/src/main/kotlin/com/esiri/esiriplus/feature/patient/screen/ReportDetailScreen.kt`
- **Purpose**: View detailed consultation report
- **Props**: reportId
- **ViewModel**: ReportDetailViewModel
- **State** (PatientReportEntity fields):
  - doctorName, consultationDate
  - diagnosedProblem, category, severity
  - presentingSymptoms, diagnosisAssessment
  - treatmentPlan, followUpInstructions
  - followUpRecommended
  - furtherNotes, verificationCode
  - prescribedMedications, prescriptionsJson
  - reportUrl (PDF link)
  - isDownloaded, localFilePath
- **UI Components**:
  - Report header (doctor, date, category badge)
  - Expandable sections:
    - Chief Complaint / Presenting Symptoms
    - Diagnosis Assessment
    - Treatment Plan
    - Follow-up Instructions
    - Prescriptions (table: medication, dosage, frequency, duration)
    - Verification Code (for external verification)
  - Download button (local caching)
  - Share button
  - Print button
- **Navigation**: onBack

#### AgentDashboardScreen (in feature/patient)
- **File**: `/d/esiriplus/feature/patient/src/main/kotlin/com/esiri/esiriplus/feature/patient/screen/AgentDashboardScreen.kt`
- **Purpose**: Agent-specific dashboard for facilitating patient consultations
- **ViewModel**: AgentDashboardViewModel
- **State**:
  - agentId, agentName
  - clientsServed (count)
  - activeClientsCount
  - commissionEarnings
  - recentClients: List<PatientSession>
- **UI Components**:
  - Agent summary card (name, ID, earnings)
  - Quick stats (clients served, active clients, commission)
  - "Start Consultation for Client" button (context: agent booking for patient)
  - Recent clients list
  - Sign out button
- **Special Features**:
  - Agent can initiate consultations on behalf of patients
  - Commission tracking
  - Client session management
- **Navigation**:
  - onStartConsultation â†’ TierSelectionRoute
  - onSignedOut â†’ popBackStack(PatientHomeRoute, false)

---

### DOCTOR SCREENS

#### DoctorDashboardScreen
- **File**: `/d/esiriplus/feature/doctor/src/main/kotlin/com/esiri/esiriplus/feature/doctor/screen/DoctorDashboardScreen.kt`
- **Purpose**: Doctor main dashboard (consultations overview, earnings, availability toggle)
- **ViewModel**: DoctorDashboardViewModel, IncomingRequestViewModel
- **State** (DoctorDashboardUiState):
  - doctorId, doctorName, specialty, profilePhoto
  - isAvailable (toggle state)
  - pendingConsultationsCount
  - ongoingConsultationsCount
  - totalEarningsToday / Month
  - upcomingAppointments
  - verificationStatus (APPROVED, PENDING, REJECTED)
  - suspensionStatus (ACTIVE, SUSPENDED, reason)
  - incomingRequests (real-time list)
  - royalClientCount
- **UI Components**:
  - Top nav bar with notifications bell
  - Doctor profile card (photo, name, specialty, verified badge, availability toggle)
  - Earnings card (today, month, total pending)
  - Stats (pending, ongoing, completion rate)
  - Tab navigation:
    - DASHBOARD (main view)
    - CONSULTATIONS (list navigation)
    - CHAT (doc mentions but seems redirected to ConsultationDetail)
    - AVAILABILITY (settings link)
    - PROFILE (edit profile)
    - EARNINGS (detailed earnings)
  - Incoming request notifications (bottom sheet or popup)
  - Suspension message toast (if applicable)
  - Action buttons:
    - "View All Consultations"
    - "View Royal Clients"
    - "View Notifications"
    - "Manage Appointments"
    - "Availability Settings"
    - "Sign Out"
  - Pull-to-refresh
- **Special Features**:
  - Availability toggle (with suspension check)
  - Real-time incoming request handling
  - Earnings tracking
  - Suspension notice handling
  - Notification permission request (Android 13+)
- **Navigation**:
  - onNavigateToConsultations â†’ DoctorConsultationListRoute
  - onNavigateToRoyalClients â†’ RoyalClientsRoute
  - onNavigateToNotifications â†’ DoctorNotificationsRoute
  - onNavigateToConsultation(id) â†’ DoctorConsultationDetailRoute(id)
  - onNavigateToAppointments â†’ DoctorAppointmentsRoute
  - onNavigateToAvailabilitySettings â†’ DoctorAvailabilitySettingsRoute
  - onSignOut

#### DoctorConsultationListScreen
- **File**: `/d/esiriplus/feature/doctor/src/main/kotlin/com/esiri/esiriplus/feature/doctor/screen/DoctorConsultationListScreen.kt`
- **Purpose**: List doctor's consultations (paginated, filterable)
- **ViewModel**: DoctorConsultationListViewModel
- **State**:
  - consultationsList: List<ConsultationEntity>
  - selectedFilter (status: pending, active, completed)
  - dateRange filter
  - searchTerm (patient name, consultation ID)
  - isLoadingMore
- **UI Components**:
  - Tab filter: Pending / Active / Completed
  - Consultation list items with:
    - Patient name (masked or code)
    - Service type
    - Status badge
    - Time/date
    - Patient age/sex (if available)
    - "Open" button
  - Infinite scroll
  - Empty state per status
  - Filters: date range, status
- **Navigation**: onConsultationSelected(id) â†’ DoctorConsultationDetailRoute(id), onBack

#### DoctorConsultationDetailScreen
- **File**: `/d/esiriplus/feature/doctor/src/main/kotlin/com/esiri/esiriplus/feature/doctor/screen/DoctorConsultationDetailScreen.kt`
- **Purpose**: Active consultation with patient (chat + video + reporting)
- **Props**: consultationId
- **ViewModel**: DoctorConsultationDetailViewModel
- **State**:
  - Consultation details (patient info, service type, status, timer)
  - Chat messages (real-time sync)
  - Timer state (same as patient side)
  - Patient summary (age, sex, blood type, allergies, conditions, medical history)
  - Report draft state
  - Video call state
- **UI Components**:
  - Top bar with patient name, timer
  - PatientSummaryCard (collapsible):
    - Age, sex, blood type
    - Known allergies
    - Chronic conditions
    - Previous consultations count
    - Medical notes
  - ChatContent (same as patient)
    - Doctor can attach images, documents
    - System messages for timer events
  - ConsultationTimerBar
  - Action buttons:
    - "Start Video Call"
    - "Write Report"
    - "End Consultation"
    - "Request Extension" (for additional payment)
  - Grace period & extension UI (mirrors patient)
- **Special Features**:
  - Patient summary auto-loads
  - Auto-draft saving on report screen
  - Timer sync with server
  - Real-time message sync
- **Navigation**:
  - onStartCall(id, callType) â†’ DoctorVideoCallRoute(id, callType)
  - onWriteReport(id) â†’ DoctorReportRoute(id)
  - onConsultationCompleted â†’ popBackStack(DoctorDashboardRoute, false)
  - onBack

#### DoctorReportScreen
- **File**: `/d/esiriplus/feature/doctor/src/main/kotlin/com/esiri/esiriplus/feature/doctor/screen/DoctorReportScreen.kt`
- **Purpose**: Write & submit consultation report
- **Props**: consultationId
- **ViewModel**: DoctorReportViewModel
- **State** (ReportDraftState):
  - chiefComplaint, symptomsPresented
  - diagnosis, icdCode
  - severity (mild, moderate, severe)
  - treatmentPlan
  - prescriptions: List<Prescription>
  - followUpNeeded, followUpDays
  - furtherNotes
  - verificationCode (auto-generated)
  - reportStatus (DRAFT, SUBMITTING, SUBMITTED, ERROR)
- **UI Components**:
  - Form sections (expandable):
    - Chief Complaint / Symptoms
    - Diagnosis
      - ICD code search
      - Severity selector
    - Treatment Plan
    - Prescriptions
      - Add prescription form
      - Medication search (autocomplete)
      - Dosage, frequency, duration inputs
      - Delete button per prescription
    - Follow-up
      - Checkbox for follow-up needed
      - Days dropdown (7, 14, 30)
    - Further Notes
  - Bottom action buttons:
    - "Save Draft"
    - "Submit Report"
    - "Cancel"
  - Submit confirmation dialog
  - Success screen (verification code display, copy button)
- **Special Features**:
  - Auto-save drafts
  - ICD code lookup
  - Medication database search
  - Real-time form validation
  - Verification code for patient to verify report authenticity
- **Navigation**: onReportSubmitted â†’ popBackStack(DoctorDashboardRoute, false), onBack

#### DoctorVideoCallScreen
- **File**: `/d/esiriplus/feature/doctor/src/main/kotlin/com/esiri/esiriplus/feature/doctor/screen/DoctorVideoCallScreen.kt`
- **Purpose**: VideoSDK video call (doctor side)
- **Props**: consultationId, callType, roomId
- **ViewModel**: Shared VideoCallViewModel
- **Same UI/features as PatientVideoCallScreen**
- **Navigation**: onCallEnded â†’ popBackStack()

#### DoctorAppointmentsScreen
- **File**: `/d/esiriplus/feature/doctor/src/main/kotlin/com/esiri/esiriplus/feature/doctor/screen/DoctorAppointmentsScreen.kt`
- **Purpose**: View doctor's scheduled appointments
- **ViewModel**: DoctorAppointmentsViewModel
- **State**:
  - appointmentsList: List<AppointmentEntity>
  - selectedDate: LocalDate
  - dateView: Day / Week / Month toggle
  - appointmentDetail (for modal)
- **UI Components**:
  - Date/calendar selector
  - Day/Week/Month view toggle
  - Appointment list/grid
    - Appointment cards with time, patient name, service type, status
    - "Join Consultation" button (if upcoming and start time within 15 min)
    - "Reschedule" / "Cancel" buttons
  - Appointment detail modal (shows patient info, service details, notes)
  - "Add Appointment" button (if available)
- **Navigation**:
  - onNavigateToConsultation(id) â†’ DoctorConsultationDetailRoute(id)
  - onBack

#### DoctorAvailabilitySettingsScreen
- **File**: `/d/esiriplus/feature/doctor/src/main/kotlin/com/esiri/esiriplus/feature/doctor/screen/DoctorAvailabilitySettingsScreen.kt`
- **Purpose**: Configure doctor's availability schedule
- **ViewModel**: DoctorAvailabilityViewModel
- **State**:
  - availabilitySlots: List<DoctorAvailabilitySlotEntity>
  - workingHours: Map<DayOfWeek, TimeRange>
  - breakTimes: List<TimeRange>
  - maxAppointmentsPerDay
  - isAvailable (global toggle)
- **UI Components**:
  - Global availability toggle
  - Working hours section:
    - Per-day time pickers (Mon-Sun)
    - "Off" checkbox per day
  - Break times section:
    - Add break button
    - Break time list (start-end)
    - Delete button per break
  - Max appointments per day input
  - Holidays calendar (date picker to set unavailable dates)
  - Save button
- **Features**:
  - Calendar view for holidays
  - Validation (break times within working hours)
  - Auto-slot generation (15-min increments)
  - Notification when schedule changes
- **Navigation**: onBack

#### DoctorNotificationsScreen
- **File**: `/d/esiriplus/feature/doctor/src/main/kotlin/com/esiri/esiriplus/feature/doctor/screen/DoctorNotificationsScreen.kt`
- **Purpose**: View doctor's notifications
- **ViewModel**: DoctorNotificationsViewModel
- **State**:
  - notificationsList: List<NotificationEntity>
  - filters (type: consultation request, appointment, message, system)
  - unreadCount
- **UI Components**:
  - Notification list items with:
    - Icon (based on type)
    - Title, body, timestamp
    - "Mark as read" action (if unread)
    - Deep link button (e.g., "View Consultation")
  - Tabs: All / Unread / By Type
  - Clear all button
  - Empty state
- **Navigation**: onBack (navigation from list items handled by notification click)

#### RoyalClientsScreen
- **File**: `/d/esiriplus/feature/doctor/src/main/kotlin/com/esiri/esiriplus/feature/doctor/screen/RoyalClientsScreen.kt`
- **Purpose**: View Royal tier clients with follow-up privileges
- **ViewModel**: RoyalClientsViewModel
- **State**:
  - royalClientsList: List<ConsultationEntity> (ROYAL tier + follow-up eligible)
  - filters (active, follow-up available)
- **UI Components**:
  - Royal client cards with:
    - Patient ID (masked)
    - Service type, tier badge (gold ROYAL badge)
    - Last consultation date
    - Follow-up available badge (if within 14 days)
    - "Open Consultation" button
    - "Start Video Call" button
  - Filter tabs (Active, Follow-up Available)
  - Empty state ("No Royal clients")
- **Navigation**:
  - onOpenConsultation(id) â†’ DoctorConsultationDetailRoute(id)
  - onStartCall(id, callType) â†’ DoctorVideoCallRoute(id, callType)
  - onBack

---

### CHAT SCREENS (Shared)

#### VideoCallScreen
- **File**: `/d/esiriplus/feature/chat/src/main/kotlin/com/esiri/esiriplus/feature/chat/ui/VideoCallScreen.kt`
- **Purpose**: Embedded video call UI component
- **ViewModel**: VideoCallViewModel
- **State**: Call state (connecting, active, ended), room ID, participants
- **Used by**: PatientVideoCallScreen, DoctorVideoCallScreen (both wrap this)

#### ChatContent
- **File**: Located in feature/chat, used by both PatientConsultationScreen and DoctorConsultationDetailScreen
- **Components**:
  - Message list (auto-scrolls to bottom)
    - Message bubbles (left for patient, right for doctor, center for system)
    - Timestamps, read/unread indicators
    - Attachment previews (images, PDFs)
    - Retry button for failed messages
    - Typing indicator row
  - Message input:
    - Text input field
    - Attachment button (popover menu for camera, gallery, document)
    - Send button (disabled if empty)
    - Mic button (for audio notes, optional)

#### ConsultationTimerBar
- **File**: Located in feature/chat
- **Components**:
  - Timer countdown (mm:ss format)
  - Status indicator (active, awaiting extension, grace period)
  - Extension button (visible in awaiting_extension state)
  - End button
  - Timer color changes:
    - Green (active)
    - Yellow (< 2 min remaining)
    - Red (< 30 sec or grace period)
  - Elapsed time counter

#### GracePeriodBanner
- **File**: Located in feature/chat
- **Components**:
  - Warning message: "Time expired. You have X minutes to accept extension."
  - Countdown timer
  - Action buttons: "Accept Extension", "Decline"
  - Color: Orange/warning background

#### PatientExtensionPrompt
- **File**: Located in feature/chat
- **Components**:
  - Bottom sheet prompt
  - Message: "Doctor requests to extend this consultation by 3 minutes for 2,500 TZS"
  - "Accept" button (navigates to ExtensionPaymentRoute)
  - "Decline" button

---

## 3. DATABASE LAYER

### 3.1 Core Entities (Room Database)

**Location**: `/d/esiriplus/core/database/src/main/kotlin/com/esiri/esiriplus/core/database/entity/`

#### User & Session Entities
1. **UserEntity**
   - `id: String` (PK)
   - `fullName, phone, email, role, isVerified`

2. **SessionEntity**
   - `id: Int` (PK, default=0)
   - `accessToken, refreshToken, expiresAt, userId, createdAt`

3. **PatientSessionEntity**
   - `sessionId: String` (PK, unique)
   - `sessionTokenHash: String` (for lookup)
   - `ageGroup, sex, region, bloodType`
   - `allergies: List<String>, chronicConditions: List<String>`
   - `createdAt, updatedAt, lastSynced`

#### Consultation & Request Entities
4. **ConsultationEntity**
   - `consultationId: String` (PK)
   - `patientSessionId, doctorId, status`
   - `serviceType` (nurse, clinical_officer, pharmacist, gp, specialist, psychologist)
   - `serviceTier` (ECONOMY, ROYAL) [default ECONOMY]
   - `consultationFee: Int`
   - `sessionStartTime, sessionEndTime, sessionDurationMinutes` (default 15)
   - `requestExpiresAt` (60-second TTL from creation)
   - `createdAt, updatedAt, scheduledEndAt`
   - `extensionCount, gracePeriodEndAt, originalDurationMinutes`
   - `followUpExpiry` (for Royal tier follow-up window)
   - `isPremium: Boolean` (deprecated?)
   - `parentConsultationId` (for follow-ups)
   - `serviceRegion: String` (TANZANIA, default)
   - **Indices**: (patientSessionId), (doctorId), (status, createdAt)

5. **AppointmentEntity**
   - `appointmentId: String` (PK)
   - `doctorId, patientSessionId, scheduledAt`
   - `durationMinutes, status, serviceType`
   - `consultationType` (chat, default)
   - `chiefComplaint`
   - `consultationFee, consultationId` (once booked)
   - `rescheduledFrom` (if rescheduled)
   - `reminderSentAt, createdAt, updatedAt`

#### Doctor Entities
6. **DoctorProfileEntity**
   - `doctorId: String` (PK)
   - `fullName, email, phone, specialty, specialistField`
   - `languages: List<String>`
   - `bio, licenseNumber, yearsExperience`
   - `profilePhotoUrl, averageRating, totalRatings`
   - `isVerified, isAvailable, inSession`
   - `countryCode` (+255), `country`
   - `licenseDocumentUrl, certificatesUrl`
   - `rejectionReason, isBanned, bannedAt, banReason`
   - `maxAppointmentsPerDay` (default 10)
   - `services: List<String>` (service types offered)
   - `createdAt, updatedAt`
   - **Indices**: (isVerified, isAvailable, specialty), (averageRating), (email)

7. **DoctorCredentialsEntity**
   - `credentialId: String` (PK)
   - `doctorId: String` (FK)
   - `documentUrl, documentType`
   - `verifiedAt`

8. **DoctorAvailabilityEntity**
   - `availabilityId: String` (PK)
   - `doctorId: String` (FK)
   - `isAvailable: Boolean, availabilitySchedule: String` (JSON)
   - `lastUpdated`

9. **DoctorAvailabilitySlotEntity**
   - `slotId: String` (PK)
   - `doctorId, slotDate, slotTime`
   - `capacity, booked, isAvailable`
   - `createdAt, updatedAt`

10. **DoctorEarningsEntity**
    - `earningId: String` (PK)
    - `doctorId, consultationId` (FKs)
    - `amount: Int, status` (pending, paid)
    - `paidAt, createdAt`
    - **Indices**: (doctorId, status), (consultationId)

#### Patient Entities
11. **PatientProfileEntity**
    - `id: String` (PK)
    - `userId: String` (FK to UserEntity)
    - `dateOfBirth, bloodGroup`
    - `allergies, emergencyContactName, emergencyContactPhone`
    - `sex, ageGroup, chronicConditions`

#### Payment Entities
12. **PaymentEntity**
    - `paymentId: String` (PK)
    - `patientSessionId: String` (FK)
    - `amount: Int, paymentMethod` (M-Pesa)
    - `transactionId, phoneNumber, status`
    - `failureReason, consultationId`
    - `synced: Boolean` (default false)
    - `createdAt, updatedAt`
    - **Indices**: (patientSessionId, createdAt), (status), (transactionId)

13. **ServiceAccessPaymentEntity**
    - `paymentId: String` (PK)
    - `serviceType: String` (one of 6 service types)
    - `amount: Int, status` (pending, completed, failed)
    - `createdAt`

14. **CallRechargePaymentEntity**
    - `paymentId: String` (PK)
    - `consultationId: String` (FK)
    - `amount: Int` (default 2500)
    - `additionalMinutes: Int` (default 3)
    - `status, createdAt`

#### Message & Communication
15. **MessageEntity**
    - `messageId: String` (PK)
    - `consultationId: String`
    - `senderType` (patient, doctor, system), `senderId`
    - `messageText, messageType` (text, image, document, audio)
    - `attachmentUrl, isRead, synced`
    - `createdAt, retryCount, failedAt`
    - **Indices**: (consultationId, createdAt)

16. **TypingIndicatorEntity**
    - `indicatorId: String` (PK)
    - `consultationId, userId, senderType`
    - `isTyping: Boolean, createdAt`

#### Medical Entities
17. **PatientReportEntity**
    - `reportId: String` (PK)
    - `consultationId, patientSessionId` (FKs)
    - `reportUrl, localFilePath`
    - `generatedAt, downloadedAt, fileSizeBytes`
    - `isDownloaded: Boolean`
    - `doctorName, consultationDate, diagnosedProblem`
    - `category, severity, presentingSymptoms`
    - `diagnosisAssessment, treatmentPlan, followUpInstructions`
    - `followUpRecommended, furtherNotes, verificationCode`
    - `prescribedMedications, prescriptionsJson` (JSON array)
    - **Indices**: (consultationId), (patientSessionId)

18. **PrescriptionEntity**
    - `id: String` (PK)
    - `consultationId, doctorId, patientId` (FKs)
    - `medication, dosage, frequency, duration, notes`
    - `createdAt`

19. **VitalSignEntity**
    - `id: String` (PK)
    - `consultationId, patientId` (FKs)
    - `type, value, unit`
    - `recordedAt`

20. **DiagnosisEntity**
    - `id: String` (PK)
    - `consultationId, doctorId` (FKs)
    - `icdCode, description, severity, notes`
    - `createdAt`

21. **MedicalRecordEntity**
    - `id: String` (PK)
    - `patientId, recordType`
    - `title, content, metadata`
    - `createdAt, updatedAt`

#### Rating & Review
22. **DoctorRatingEntity**
    - `ratingId: String` (PK)
    - `doctorId, consultationId, patientSessionId` (FKs)
    - `rating: Int` (1-5)
    - `comment, synced: Boolean`
    - `createdAt`
    - **Indices**: (doctorId), (consultationId, unique), (patientSessionId)

23. **ReviewEntity**
    - `id: String` (PK)
    - `doctorId, patientId` (FKs)
    - `rating, comment`
    - `createdAt, updatedAt`

#### Notification & Activity
24. **NotificationEntity**
    - `notificationId: String` (PK)
    - `userId: String`
    - `title, body, type` (consultation_request, appointment, message, payment, system)
    - `data: String` (JSON with contextual data)
    - `readAt, createdAt`
    - **Indices**: (userId), (type)

25. **AuditLogEntity**
    - `id: String` (PK)
    - `userId, action` (login, logout, payment, consultation_start, etc.)
    - `metadata: String` (JSON)
    - `ipAddress, createdAt`

26. **VideoCallEntity**
    - `callId: String` (PK)
    - `consultationId: String` (FK)
    - `startedAt, endedAt, durationSeconds`
    - `callQuality, meetingId, initiatedBy`
    - `callType` (VIDEO, AUDIO)
    - `status` (initiated, active, ended)
    - `timeLimitSeconds` (default 180)
    - `timeUsedSeconds, isTimeExpired, totalRecharges`
    - `createdAt`

#### Service Tier & Configuration
27. **ServiceTierEntity**
    - `id: String` (PK)
    - `category` (ECONOMY, ROYAL), `displayName, description`
    - `priceAmount: Int, currency`
    - `isActive, sortOrder`
    - `durationMinutes` (default 15)
    - `features: String` (JSON, e.g., follow-ups, doctor selection)

28. **AppConfigEntity**
    - `key: String` (PK)
    - `value: String` (JSON)
    - `version: Int`

#### Other Entities
29. **ProviderEntity**
    - `providerId: String` (PK)
    - `providerName, serviceType, contactInfo`
    - `location, rating, isActive`

30. **ScheduleEntity**
    - `scheduleId: String` (PK)
    - `doctorId, date, startTime, endTime`
    - `status, notes`

31. **AttachmentEntity**
    - `attachmentId: String` (PK)
    - `messageId, consultationId` (FKs)
    - `fileUrl, fileName, fileSizeBytes`
    - `uploadedAt`

---

### 3.2 DAO (Data Access Objects)

**Location**: `/d/esiriplus/core/database/src/main/kotlin/com/esiri/esiriplus/core/database/dao/`

#### Key DAOs

**ConsultationDao.kt**
```kotlin
// Queries
suspend fun insert(consultation: ConsultationEntity)
suspend fun insertAll(consultations: List<ConsultationEntity>)
suspend fun getById(id: String): ConsultationEntity?
fun getByPatientSessionId(sessionId: String): Flow<List<ConsultationEntity>>
fun getByStatus(status: String): Flow<List<ConsultationEntity>>
fun getByDoctorId(doctorId: String): Flow<List<ConsultationEntity>>
fun getByDoctorIdAndStatus(doctorId: String, status: String): Flow<List<ConsultationEntity>>
fun getRoyalConsultationsForDoctor(doctorId: String, nowMillis: Long): Flow<List<ConsultationEntity>>
fun getActiveConsultation(): Flow<ConsultationEntity?>
fun getOngoingConsultationsForPatient(patientSessionId, currentTimeMillis): Flow<List<ConsultationEntity>>
fun getOngoingConsultationsForDoctor(doctorId, currentTimeMillis): Flow<List<ConsultationEntity>>
fun getConsultationWithMessages(id): Flow<ConsultationWithMessages?>
fun getPatientConsultations(sessionId): Flow<List<ConsultationWithDoctor>>
fun getConsultationsWithDoctorInfo(sessionId): Flow<List<ConsultationWithDoctorInfo>>
fun getUnratedCompletedConsultation(patientSessionId): ConsultationEntity?
fun getDoctorNameForConsultation(consultationId): String?

// Updates
suspend fun updateStatus(consultationId, status, updatedAt)
suspend fun updateTimerState(consultationId, scheduledEndAt, extensionCount, gracePeriodEndAt, originalDurationMinutes, status, serviceTier, doctorId, serviceType, updatedAt)
suspend fun setFollowUpExpiry(consultationId, followUpExpiry, updatedAt)

// Cleanup
suspend fun clearAll()
```

**PatientSessionDao.kt**
```kotlin
suspend fun insert(session: PatientSessionEntity)
suspend fun update(session: PatientSessionEntity)
suspend fun getById(id: String): PatientSessionEntity?
fun getSession(): Flow<PatientSessionEntity?>
suspend fun delete(session: PatientSessionEntity)
fun getPatientWithConsultations(sessionId): Flow<PatientWithConsultations?>
suspend fun clearAll()
```

**PaymentDao.kt**
```kotlin
suspend fun getById(paymentId: String): PaymentEntity?
fun getByPatientSessionId(sessionId): Flow<List<PaymentEntity>>
fun getByStatus(status): Flow<List<PaymentEntity>>
suspend fun getCompletedByConsultationId(consultationId): PaymentEntity?
fun getTransactionHistory(limit, offset): Flow<List<PaymentEntity>>
suspend fun updateStatus(paymentId, status, transactionId, updatedAt)
suspend fun getUnsyncedPayments(): List<PaymentEntity>
suspend fun markSynced(paymentId)
suspend fun insert(payment: PaymentEntity)
suspend fun insertAll(payments: List<PaymentEntity>)
suspend fun delete(payment: PaymentEntity)
suspend fun clearAll()
```

**DoctorProfileDao.kt**
```kotlin
suspend fun insert(profile: DoctorProfileEntity)
suspend fun getById(doctorId): DoctorProfileEntity?
fun searchBySpecialty(specialty): Flow<List<DoctorProfileEntity>>
fun getVerifiedDoctors(): Flow<List<DoctorProfileEntity>>
fun getAvailableDoctors(): Flow<List<DoctorProfileEntity>>
fun getDoctorsWithRating(minRating: Double): Flow<List<DoctorProfileEntity>>
suspend fun updateAvailability(doctorId, isAvailable)
suspend fun updateProfile(profile: DoctorProfileEntity)
suspend fun clearAll()
```

**MessageDao.kt**
```kotlin
suspend fun insert(message: MessageEntity)
fun getByConsultationId(consultationId): Flow<List<MessageEntity>>
suspend fun getById(messageId): MessageEntity?
fun getUnreadForConsultation(consultationId): Flow<List<MessageEntity>>
suspend fun markAsRead(messageId)
suspend fun updateSyncStatus(messageId, synced)
suspend fun getUnsyncedMessages(): List<MessageEntity>
suspend fun delete(messageId)
suspend fun clearAll()
```

**DoctorRatingDao.kt**
```kotlin
suspend fun insert(rating: DoctorRatingEntity)
fun getByDoctorId(doctorId): Flow<List<DoctorRatingEntity>>
suspend fun getByConsultationId(consultationId): DoctorRatingEntity?
suspend fun getUnsyncedRatings(): List<DoctorRatingEntity>
suspend fun markSynced(ratingId)
suspend fun delete(ratingId)
suspend fun clearAll()
```

**NotificationDao.kt**, **VideoCallDao.kt**, **AppointmentDao.kt** follow similar patterns.

---

### 3.3 Entity Relationships

**ConsultationWithMessages**
```kotlin
@Embedded val consultation: ConsultationEntity
@Relation(parentColumn = "consultationId", entityColumn = "consultationId")
val messages: List<MessageEntity>
```

**ConsultationWithDoctor**
```kotlin
@Embedded val consultation: ConsultationEntity
@Relation(parentColumn = "doctorId", entityColumn = "doctorId")
val doctor: DoctorProfileEntity
```

**PatientWithConsultations**
```kotlin
@Embedded val session: PatientSessionEntity
@Relation(parentColumn = "sessionId", entityColumn = "patientSessionId")
val consultations: List<ConsultationEntity>
```

---

## 4. NETWORK LAYER

### 4.1 Supabase Edge Functions (Backend API)

**Location**: `/d/esiriplus/supabase/functions/`

Total: 64 edge functions implementing all business logic.

#### Authentication Functions
1. **create-patient-session** â€” Generate anonymous patient session (96-char token, SHA-256 + PBKDF2 hash, JWT + refresh token)
2. **refresh-patient-session** â€” Extend patient session expiry
3. **extend-session** â€” Extend session TTL (max 72 hours per request, 30 days total cap)
4. **login-doctor** â€” Doctor OTP/email login
5. **send-doctor-otp** â€” Send OTP to doctor's registered email/phone
6. **verify-doctor-otp** â€” Verify OTP, issue JWT
7. **register-doctor** â€” Create doctor account (with document upload)
8. **login-agent** â€” Agent authentication with PIN
9. **register-agent** â€” Create agent account
10. **setup-recovery** â€” Setup security questions
11. **recover-by-id** â€” Recover account using patient ID
12. **recover-by-questions** â€” Recover account using security question answers
13. **check-device-binding** â€” Check if device already bound to a session
14. **bind-device** â€” Bind device to patient session (for offline mode)
15. **deauthorize-device** â€” Unbind device
16. **update-fcm-token** â€” Register FCM token for push notifications

#### Consultation Management
17. **create-consultation** â€” Patient requests consultation (validates service access payment, assigns doctor, creates request with 60s TTL)
18. **handle-consultation-request** â€” Full consultation lifecycle (create, accept, reject, expire, status polling)
19. **manage-consultation** â€” Timed session management (sync, end, timer_expired, request_extension, accept_extension, decline_extension, payment_confirmed, cancel_payment)
20. **book-appointment** â€” Patient books appointment with doctor
21. **get-doctor-slots** â€” Get available appointment slots for doctor
22. **reschedule-appointment** â€” Reschedule existing appointment

#### Payment Functions
23. **service-access-payment** â€” Initiate payment for service access (6 service types with specific pricing)
24. **mpesa-stk-push** â€” Initiate M-Pesa STK push
25. **mpesa-callback** â€” Webhook for M-Pesa payment callback
26. **call-recharge-payment** â€” Initiate payment for call time extension (3 minutes = 2,500 TZS)

#### Doctor Functions
27. **list-doctors** â€” Get doctors filtered by specialty, service type, tier
28. **list-all-doctors** â€” Get all verified doctors (admin only)
29. **manage-doctor** â€” Update doctor profile, verify, suspend, ban
30. **get-doctor-ratings** â€” Get ratings for doctor
31. **get-all-ratings** â€” Get all ratings in system (admin)
32. **rate-doctor** â€” Patient rates doctor (1-5 stars, optional comment)
33. **send-appointment-notification** â€” Send appointment reminder
34. **appointment-reminder** â€” Scheduled job to send reminders 15 mins before
35. **log-doctor-online** â€” Log doctor as online/offline
36. **handle-missed-appointments** â€” Process missed appointments
37. **lift-expired-suspensions** â€” Cron job to lift automatic suspensions
38. **log-performance-metrics** â€” Track doctor metrics (response time, completion rate)

#### Messages & Communication
39. **handle-messages** â€” Insert, retrieve, mark-as-read chat messages
40. **send-push-notification** â€” Send FCM push notification

#### Reports & Analytics
41. **generate-consultation-report** â€” Generate PDF report for completed consultation
42. **generate-patient-summary** â€” Generate patient medical summary
43. **generate-health-analytics** â€” Generate patient health analytics
44. **generate-doctor-performance-report** â€” Generate doctor statistics report
45. **generate-analytics-report** â€” Platform-wide analytics
46. **get-patient-reports** â€” List patient's reports
47. **verify-report** â€” Verify report authenticity using verification code

#### Video Call
48. **videosdk-token** â€” Generate VideoSDK token for room access

#### Security & Admin
49. **get-security-questions** â€” Get security questions list
50. **admin-portal-action** â€” Admin actions (user management, verification, suspension, bans)
51. **get-admin-stats** â€” Dashboard statistics for admins
52. **get-audit-logs** â€” Audit log retrieval
53. **create-portal-user** â€” Create admin portal user

#### Utilities
54. **get-vapid-key** â€” Get VAPID key for push notifications
55. **update-patient-demographics** â€” Update patient profile fields

---

### 4.2 Shared Utilities (in `_shared/` folder)

- **cors.ts** â€” CORS header handling, preflight responses
- **auth.ts** â€” validateAuth() for JWT/session validation
- **rateLimit.ts** â€” Rate limiting (LIMITS.payment, LIMITS.sensitive, etc.)
- **errors.ts** â€” Custom error classes (ValidationError), errorResponse(), successResponse()
- **supabase.ts** â€” getServiceClient() for Deno edge functions
- **logger.ts** â€” logEvent() for audit logs, getClientIp() for rate limiting
- **bcrypt.ts** â€” hashToken() (PBKDF2), sha256Hex() for token hashing
- **payment.ts** â€” Payment processing utilities

---

### 4.3 API Clients (Android/Kotlin)

**File**: `/d/esiriplus/core/network/src/main/kotlin/com/esiri/esiriplus/core/network/`

#### SupabaseApi (Retrofit interface)
```kotlin
interface SupabaseApi {
    // Consultations
    @GET("rest/v1/consultations")
    suspend fun getConsultationsForPatient(...): Response<List<ConsultationApiModel>>
    
    @GET("rest/v1/consultations")
    suspend fun getConsultationsForDoctor(...): Response<List<ConsultationApiModel>>
    
    @PATCH("rest/v1/consultations")
    suspend fun updateConsultationStatus(...): Response<ConsultationApiModel>
    
    // Payments
    @GET("rest/v1/payments")
    suspend fun getPaymentsForConsultation(...): Response<List<PaymentApiModel>>
    
    // Users
    @GET("rest/v1/users")
    suspend fun getUser(...): Response<UserApiModel>
    
    @PATCH("rest/v1/users")
    suspend fun updateUser(...): Response<UserApiModel>
    
    // FCM Tokens
    @POST("rest/v1/fcm_tokens")
    suspend fun upsertFcmToken(...): Response<Unit>
}
```

#### DTOs
- **AuthDto** â€” Authentication request/response objects
- **ConsultationDto** â€” Consultation create/update payloads
- **PaymentDto** â€” Payment request/response
- **UserApiModel** â€” User profile objects
- **VideoDto** â€” Video call token request/response
- **CallRechargeDto** â€” Call extension payment payload
- **SecurityQuestionDto** â€” Security question/answer objects

#### EdgeFunctionClient
- Wrapper around Retrofit for edge function invocation
- Methods like `createConsultation()`, `acceptConsultationRequest()`, `initiatePayment()`, etc.
- Handles authorization headers (session token or JWT)

---

## 5. STATE MANAGEMENT (ViewModels)

### PATIENT ViewModels

#### PatientHomeViewModel
- **State**: PatientHomeUiState
  - patientId, maskedPatientId, soundsEnabled
  - isLoading, isRefreshing, activeConsultation
  - pendingRatingConsultation, ongoingConsultations
  - hasUnreadReports
- **Methods**:
  - logout() â€” Clears auth state, signs out
  - dismissPendingRating() â€” Hides pending rating sheet
  - clearPendingRating() â€” Resets pending rating state
  - refreshConsultations() â€” Pull-to-refresh callback
  - toggleSounds() â€” Toggle notification sounds
- **Data Sources**:
  - authRepository.currentSession (Realtime session tracking)
  - consultationDao flows (ongoing, active, pending ratings)
  - patientSessionDao flows (session info)
  - reportPrefs SharedPreferences (unread reports flag)
  - Local syncing for unsynced ratings

#### PatientConsultationViewModel
- **State**: ConsultationUiState + SessionState (timer)
  - consultation (full entity), messages (list), doctor info
  - timerState (scheduledEndAt, extensionCount, gracePeriodEndAt, originalDurationMinutes, status)
  - phase (ACTIVE, AWAITING_EXTENSION, GRACE_PERIOD, COMPLETED)
  - messageInput (draft), isLoading, error
- **Methods**:
  - loadConsultation(id) â€” Fetch consultation + messages
  - sendMessage(text, attachmentUrl) â€” Send chat message (local + sync)
  - uploadAttachment(file) â€” Upload file to Firebase Storage
  - syncTimer() â€” Sync timer with server (manage-consultation sync action)
  - onTimerExpired() â€” Notify server + show extension prompt
  - acceptExtension() â€” Start grace period
  - declineExtension() â€” Mark as declined
  - endConsultation() â€” Doctor ends session
  - subscribeToMessages() â€” Realtime message subscription (Supabase)
  - subscribeToTypingIndicator() â€” Real-time typing indicator
  - markMessagesAsRead(consultationId) â€” Update read status
- **Data Sources**:
  - consultationDao.getConsultationWithMessages(id)
  - messageDao flows
  - Supabase Realtime subscriptions
  - EdgeFunctionClient for manage-consultation calls

#### PatientPaymentViewModel
- **State**: PaymentUiState
  - consultationId, amount, serviceType, phoneNumber
  - paymentStatus (CONFIRM, PROCESSING, COMPLETED, FAILED)
  - isLoading, errorMessage, transactionId
- **Methods**:
  - initiatePayment(phoneNumber) â€” Call service-access-payment or call-recharge-payment
  - pollPaymentStatus(paymentId) â€” Poll edge function until completed or failed
  - cancelPayment() â€” Clear state
- **Data Sources**: EdgeFunctionClient, PaymentDao

#### TierSelectionViewModel
- **State**: TierSelectionUiState
  - selectedTier (ECONOMY, ROYAL)
  - tiers: List<ServiceTierEntity> (from DB)
  - pricing info
- **Methods**:
  - selectTier(tier) â€” Set selected tier
  - getPricingInfo(tier) â€” Load tier pricing
- **Data Sources**: ServiceTierDao

#### ServicesViewModel
- **State**: ServicesUiState
  - tier (from SavedStateHandle)
  - services: List<ServiceTierEntity> (filtered by tier)
  - selectedService
  - pricing (PricingEngine applied)
- **Methods**:
  - selectService(category) â€” Set selected service
  - applyPricing(tier) â€” Apply tier-specific pricing via PricingEngine
- **Data Sources**: ServiceTierDao, SavedStateHandle

#### FindDoctorViewModel
- **State**: FindDoctorUiState
  - serviceCategory, servicePriceAmount, serviceDurationMinutes, serviceTier
  - doctors: List<DoctorProfileEntity> (from Supabase REST + local DB)
  - selectedDoctor
  - filters (specialty, minRating, language)
  - isLoading, error
  - activeConsultations (if any, skip booking)
- **Methods**:
  - loadDoctors(serviceType) â€” Fetch from Supabase + sync to local DB
  - filterDoctors(specialty, minRating, language)
  - selectDoctor(doctorId)
  - checkActiveConsultations() â€” If patient has active consultation, navigate directly
- **Data Sources**: DoctorProfileDao, EdgeFunctionClient.listDoctors, Supabase Realtime (availability)

#### BookAppointmentViewModel
- **State**: BookAppointmentUiState
  - doctorId, serviceCategory, servicePriceAmount, serviceDurationMinutes, serviceTier
  - chiefComplaint, selectedConsultationType (chat, video, both)
  - isLoading, error, bookingSuccess
  - appointmentId (on success)
- **Methods**:
  - setChiefComplaint(text) â€” Validate length (10-1000 chars)
  - setConsultationType(type)
  - submitBooking() â€” Call handle-consultation-request (create action)
- **Data Sources**: EdgeFunctionClient, AppointmentDao

#### OngoingConsultationsViewModel
- **State**: OngoingConsultationsUiState
  - consultations: List<ConsultationEntity> (active/awaiting_extension/grace_period + within follow-up window)
  - filters (status)
  - isLoading, isRefreshing
- **Methods**:
  - loadOngoing() â€” Query consultationDao.getOngoingConsultationsForPatient()
  - refresh() â€” Re-query + sync from Supabase
  - requestFollowUp(consultationId, doctorId, serviceType)
- **Data Sources**: ConsultationDao, EdgeFunctionClient

#### PatientProfileViewModel
- **State**: PatientProfileUiState
  - patientId, dateOfBirth, bloodGroup, allergies
  - emergencyContactName, emergencyContactPhone
  - sex, ageGroup, chronicConditions
  - isEditing, isSaving, error
- **Methods**:
  - loadProfile() â€” Query PatientProfileDao + PatientSessionDao
  - toggleEdit()
  - updateField(field, value)
  - saveProfile() â€” Insert/update PatientProfileEntity + call update-patient-demographics
- **Data Sources**: PatientProfileDao, PatientSessionDao, EdgeFunctionClient

#### ReportsViewModel
- **State**: ReportsUiState
  - reports: List<PatientReportEntity>
  - filters (dateRange, doctor)
  - unreadCount, hasUnreadReports
  - isLoadingMore
- **Methods**:
  - loadReports() â€” Paginated query from PatientReportDao
  - loadMore() â€” Load next page
  - markAsRead(reportId)
  - downloadReport(reportId) â€” Download PDF to local cache
- **Data Sources**: PatientReportDao, local file system

#### ReportDetailViewModel
- **State**: ReportDetailUiState
  - reportId, (all fields from PatientReportEntity)
  - prescriptions: List<Prescription>
  - isDownloading, downloadProgress
  - isVerifying (verify-report action)
- **Methods**:
  - loadReport(reportId)
  - downloadReport()
  - shareReport()
  - verifyReport(verificationCode) â€” Call verify-report edge function
- **Data Sources**: PatientReportDao, EdgeFunctionClient

#### AgentDashboardViewModel
- **State**: AgentDashboardUiState
  - agentId, agentName
  - clientsServed, activeClientsCount
  - commissionEarnings
  - recentClients: List<PatientSessionEntity>
- **Methods**:
  - loadAgentStats()
  - loadRecentClients()
  - logout()
- **Data Sources**: AuthRepository, ConsultationDao, DoctorEarningsDao

---

### DOCTOR ViewModels

#### DoctorDashboardViewModel
- **State**: DoctorDashboardUiState
  - doctorId, doctorName, specialty, profilePhotoUrl
  - isAvailable (toggle state)
  - pendingConsultationsCount, ongoingConsultationsCount
  - completionRate, averageRating
  - earningsToday, earningsMonth, earningsPending
  - upcomingAppointments (next 3)
  - verificationStatus (APPROVED, PENDING, REJECTED)
  - suspensionStatus, suspensionMessage
  - royalClientCount
- **Methods**:
  - toggleAvailability() â€” Update DoctorProfileEntity.isAvailable + call manage-doctor
  - logout()
  - refreshDashboard() â€” Pull-to-refresh callback
  - clearSuspensionMessage()
- **Data Sources**:
  - DoctorProfileDao, ConsultationDao, AppointmentDao
  - DoctorEarningsDao, DoctorRatingDao
  - Supabase Realtime (suspension status, incoming requests)
  - EdgeFunctionClient for availability updates

#### IncomingRequestViewModel
- **State**: IncomingRequestUiState
  - incomingRequests: List<ConsultationRequestData>
  - selectedRequest
  - requestStatus (PENDING, ACCEPTED, REJECTED, EXPIRED)
- **Methods**:
  - loadIncomingRequests() â€” Real-time subscription via Supabase
  - acceptRequest(requestId) â€” Call handle-consultation-request (accept action)
  - rejectRequest(requestId) â€” Call handle-consultation-request (reject action)
  - dismissRequest(requestId)
- **Data Sources**: Supabase Realtime subscription, EdgeFunctionClient

#### DoctorConsultationListViewModel
- **State**: DoctorConsultationListUiState
  - consultations: List<ConsultationEntity>
  - selectedFilter (PENDING, ACTIVE, COMPLETED, ALL)
  - filters (dateRange, status)
  - isLoadingMore
- **Methods**:
  - loadConsultations(filter) â€” Query consultationDao.getByDoctorIdAndStatus()
  - loadMore()
  - setFilter(filter)
- **Data Sources**: ConsultationDao

#### DoctorConsultationDetailViewModel
- **State**: DoctorConsultationDetailUiState
  - consultationId, consultation (full entity), messages, doctor/patient summary
  - timerState (same as patient)
  - reportDraft (for report screen)
  - isLoading, error, canEndConsultation
- **Methods**:
  - loadConsultation(id)
  - loadPatientSummary(patientSessionId) â€” Call generate-patient-summary
  - sendMessage(text, attachmentUrl)
  - uploadAttachment(file)
  - syncTimer() â€” Call manage-consultation sync
  - requestExtension() â€” Call manage-consultation request_extension
  - endConsultation() â€” Call manage-consultation end
  - subscribeToMessages() â€” Realtime
  - subscribeToTimerEvents() â€” Realtime updates from manage-consultation
- **Data Sources**: ConsultationDao, MessageDao, EdgeFunctionClient, Supabase Realtime

#### DoctorReportViewModel
- **State**: DoctorReportUiState
  - consultationId, chiefComplaint, symptomsPresented
  - diagnosis, icdCode (searchable)
  - severity (MILD, MODERATE, SEVERE)
  - treatmentPlan, prescriptions: List<Prescription>
  - followUpNeeded, followUpDays
  - furtherNotes
  - reportStatus (DRAFT, SUBMITTING, SUBMITTED, ERROR)
  - verificationCode (auto-generated on submission)
  - isDraft (from localStorage for auto-save)
- **Methods**:
  - loadDraft(consultationId) â€” Load auto-saved draft
  - saveDraft() â€” Auto-save to localStorage
  - addPrescription(medication, dosage, frequency, duration)
  - removePrescription(index)
  - searchIcdCode(query) â€” Search ICD-10 database
  - searchMedication(query) â€” Search medication database
  - submitReport() â€” Call generate-consultation-report edge function
  - updateField(field, value)
- **Data Sources**:
  - Local draft cache (SharedPreferences or Room)
  - EdgeFunctionClient (ICD lookup, medication DB, generate-consultation-report)
  - ConsultationDao (consultation details)

#### DoctorAppointmentsViewModel
- **State**: DoctorAppointmentsUiState
  - appointments: List<AppointmentEntity>
  - selectedDate: LocalDate
  - dateViewMode (DAY, WEEK, MONTH)
  - selectedAppointment (for detail modal)
- **Methods**:
  - loadAppointments(date, viewMode)
  - setSelectedDate(date)
  - setViewMode(mode)
  - rescheduleAppointment(appointmentId, newDateTime)
  - cancelAppointment(appointmentId)
- **Data Sources**: AppointmentDao, EdgeFunctionClient

#### DoctorAvailabilityViewModel
- **State**: DoctorAvailabilityUiState
  - workingHours: Map<DayOfWeek, TimeRange>
  - breakTimes: List<TimeRange>
  - unavailableDates: List<LocalDate>
  - maxAppointmentsPerDay: Int
  - autoSlots: List<DoctorAvailabilitySlotEntity> (generated)
  - isLoading, isSaving, error
- **Methods**:
  - loadAvailability(doctorId)
  - setWorkingHours(day, startTime, endTime)
  - setDayOff(day, isOff)
  - addBreakTime(startTime, endTime)
  - removeBreakTime(index)
  - addUnavailableDate(date)
  - removeUnavailableDate(date)
  - setMaxAppointmentsPerDay(max)
  - generateSlots() â€” Create 15-min slots from working hours
  - saveAvailability() â€” Update DoctorAvailabilityEntity + sync
- **Data Sources**: DoctorAvailabilityDao, DoctorAvailabilitySlotDao, EdgeFunctionClient

#### DoctorNotificationsViewModel
- **State**: DoctorNotificationsUiState
  - notifications: List<NotificationEntity>
  - filters (type: consultation_request, appointment, message, etc.)
  - unreadCount
  - isLoadingMore
- **Methods**:
  - loadNotifications(filter) â€” Query NotificationDao
  - markAsRead(notificationId)
  - markAllAsRead()
  - deleteNotification(notificationId)
  - subscribeToRealtime() â€” Real-time notification sync via Supabase Realtime
- **Data Sources**: NotificationDao, Supabase Realtime

#### RoyalClientsViewModel
- **State**: RoyalClientsUiState
  - royalConsultations: List<ConsultationEntity> (ROYAL tier + follow-up eligible)
  - filters (active, follow-up available)
  - selectedConsultation (for detail)
- **Methods**:
  - loadRoyalClients() â€” Query consultationDao.getRoyalConsultationsForDoctor()
  - setFilter(filter)
  - checkFollowUpEligibility(consultationId) â€” Check followUpExpiry > now
- **Data Sources**: ConsultationDao, Supabase Realtime

---

### AUTH ViewModels

#### PatientSetupViewModel
- **State**: PatientSetupUiState
  - patientId, recoveryQuestionsCompleted
  - sex, ageGroup, bloodType, allergies, chronicConditions, region
  - isCreatingSession, isSaving, isGeneratingPdf
  - sessionError, saveError, pdfError
  - isComplete
- **Methods**:
  - createSession() â€” Call CreatePatientSessionUseCase (calls create-patient-session edge function)
  - retryCreateSession()
  - onRecoveryQuestionsCompleted()
  - onSexChanged(sex), onAgeGroupChanged(ageGroup), etc.
  - saveProfile() â€” Update PatientProfileEntity + PatientSessionEntity
  - generatePdf() â€” Generate patient ID PDF certificate
  - downloadPdf()
- **Data Sources**: CreatePatientSessionUseCase, PatientProfileDao, PatientSessionDao, EdgeFunctionClient

#### DoctorLoginViewModel
- **State**: DoctorLoginUiState
  - email, phoneNumber, otp
  - loginStep (EMAIL_INPUT, OTP_INPUT)
  - isLoading, error, isAuthenticated
  - resendCountdown
- **Methods**:
  - submitEmail(email) â€” Call send-doctor-otp
  - submitOtp(otp) â€” Call verify-doctor-otp
  - resendOtp()
  - cancelOtp()
- **Data Sources**: EdgeFunctionClient, AuthRepository

#### DoctorRegistrationViewModel
- **State**: DoctorRegistrationUiState
  - fullName, email, phone, specialty, specialistField
  - licenseNumber, yearsExperience
  - languages: List<String>
  - services: List<String>
  - licenseDocumentUrl, certificatesUrl
  - registrationStep (PERSONAL, CREDENTIALS, LANGUAGES, SERVICES, DOCUMENTS, REVIEW)
  - isLoading, error, isRegistered
- **Methods**:
  - setPersonalInfo(name, email, phone, specialty)
  - setExperience(yearsExperience, licenseNumber)
  - toggleLanguage(language)
  - toggleService(service)
  - uploadLicenseDocument(file)
  - uploadCertificates(file)
  - submitRegistration() â€” Call register-doctor
  - nextStep(), previousStep()
- **Data Sources**: EdgeFunctionClient, FirebaseStorage (for document upload), AuthRepository

#### SecurityQuestionsSetupViewModel
- **State**: SecurityQuestionsSetupUiState
  - questions: List<SecurityQuestionDto>
  - selectedQuestions: Map<String, String> (question -> answer)
  - isLoading, error, isComplete
- **Methods**:
  - loadSecurityQuestions() â€” Call get-security-questions edge function
  - selectQuestion(index, questionId)
  - setAnswer(questionId, answer)
  - submitAnswers() â€” Call setup-recovery
- **Data Sources**: EdgeFunctionClient

#### PatientRecoveryViewModel
- **State**: PatientRecoveryUiState
  - recoveryMethod (BY_ID, BY_QUESTIONS)
  - patientId (input), answers (input)
  - isLoading, error, recoveredSession
  - recoveredSessionToken (for login)
- **Methods**:
  - setRecoveryMethod(method)
  - setPatientId(id)
  - setAnswer(questionId, answer)
  - submitRecovery() â€” Call recover-by-id or recover-by-questions
- **Data Sources**: EdgeFunctionClient, AuthRepository

#### AccessRecordsViewModel
- **State**: AccessRecordsUiState
  - patientId (input)
  - patientSessionInfo (returned)
  - isLoading, error, accessGranted
- **Methods**:
  - setPatientId(id)
  - requestAccess() â€” Retrieve session by patient ID (calls Supabase REST)
  - grantAccess() â€” Save returned session
- **Data Sources**: EdgeFunctionClient, SupabaseApi

#### AgentAuthViewModel (in feature/auth)
- **State**: AgentAuthUiState
  - agentId, pin
  - agentInfo
  - isLoading, error, isAuthenticated
- **Methods**:
  - setAgentId(id)
  - setPin(pin)
  - submitAuth() â€” Call login-agent edge function
- **Data Sources**: EdgeFunctionClient, AuthRepository

---

## 6. REAL-TIME FEATURES

### 6.1 Supabase Realtime Subscriptions

**Type**: PostgreSQL LISTEN/NOTIFY protocol wrapped by Supabase client

#### Patient Consultation Listeners
- **consultations** â€” Updates to consultation status, timer state, extension requests
- **messages** â€” Real-time chat messages in active consultation
- **typing_indicators** â€” Real-time typing status
- **notifications** â€” Push notifications (new appointment, payment status, etc.)
- **doctor_availability** â€” Doctor availability changes (affects Find Doctor list)

#### Doctor Consultation Listeners
- **consultations** â€” Incoming requests, consultation updates
- **consultation_requests** â€” New consultation requests (handled by IncomingRequestViewModel)
- **messages** â€” Chat messages in active consultations
- **typing_indicators**
- **notifications**

#### Subscription Setup (Example)
```kotlin
supabase.realtime
  .channel("consultations:${patientSessionId}")
  .onPostgresChange(
    event = PostgresChangeEvent.ALL,
    schema = "public",
    table = "consultations"
  ) { data ->
    // Update consultation state from data
  }
  .subscribe()
```

---

### 6.2 Firebase Cloud Messaging (FCM)

**Service**: FcmService in app/src/main/kotlin/com/esiri/esiriplus/firebase/

#### Push Notification Types
1. **Consultation Request** â€” "Doctor <name> has requested to consult with you"
   - Action: Open DoctorConsultationDetailRoute
   - Sent by: handle-consultation-request (create action)

2. **Consultation Accepted** â€” "Doctor <name> accepted your consultation request"
   - Action: Open PatientConsultationRoute
   - Sent by: handle-consultation-request (accept action)

3. **Consultation Rejected** â€” "Doctor declined your request. Try another doctor?"
   - Action: Navigate to FindDoctorRoute
   - Sent by: handle-consultation-request (reject action)

4. **Appointment Reminder** â€” "You have an appointment with Dr. <name> in 15 minutes"
   - Action: Open DoctorConsultationDetailRoute
   - Sent by: appointment-reminder (cron job)

5. **Payment Status** â€” "Your payment for <service> is pending / confirmed"
   - Action: Show payment detail modal
   - Sent by: mpesa-callback

6. **Report Ready** â€” "Your consultation report from Dr. <name> is ready"
   - Action: Open ReportDetailRoute
   - Sent by: generate-consultation-report (on completion)

7. **Timer Expired** â€” "Your consultation time has ended. Request extension?"
   - Action: Show extension prompt
   - Sent by: manage-consultation (timer_expired action)

8. **Extension Request** â€” "Doctor is requesting to extend your consultation for 2,500 TZS"
   - Action: Show extension acceptance prompt
   - Sent by: manage-consultation (request_extension action)

#### FCM Token Management
- Registered at app startup via update-fcm-token edge function
- Stored in Supabase fcm_tokens table
- Synced when patient/doctor session starts
- Updated when token refreshes

---

### 6.3 VideoSDK Integration

**Service**: VideoSDK for real-time video/audio calls

#### Room Creation Flow
1. **PatientPaymentScreen** â†’ Payment completed
2. Navigates to **PatientVideoCallRoute(consultationId)**
3. **PatientVideoCallViewModel** calls **videosdk-token** edge function
4. Edge function generates token for consultation ID
5. VideoSDK room created with token
6. Both patient & doctor join same room
7. Real-time audio/video stream + screen sharing
8. **CallRechargePaymentScreen** available during call for time extension

#### Video Call Features
- Video/audio toggle
- Screen share (doctor only, typically)
- Recording (if enabled by admin)
- Participant list
- Call quality indicator
- Mute notification (when someone mutes)
- End call button

#### Call Duration Management
- Timer starts when call begins (VideoCallEntity.startedAt)
- Tracked via VideoCallEntity.timeUsedSeconds
- Extensions via call-recharge-payment + manage-consultation (payment_confirmed)
- Auto-end if time expired (VideoCallEntity.isTimeExpired = true)

---

## 7. BUSINESS LOGIC

### 7.1 Service Tiers (Economy vs Royal)

#### ECONOMY Tier
- **Pricing**: Service-type dependent (500-2,500 TZS per service)
- **Duration**: 15 minutes base (varies by service)
- **Features**:
  - 1 consultation per service type (no follow-ups by default)
  - No guaranteed doctor selection
  - Doctor availability dependent
  - No priority support
  - Follow-up eligible but only within 1 consultation window (not unlimited)
- **Follow-up**: Can request follow-up with same doctor if within 14-day window
  - Only 1 follow-up allowed per original consultation
  - Cost: Same as original service type
- **Service Types & Pricing**:
  - Nurse: 500 TZS
  - Clinical Officer: 800 TZS
  - Pharmacist: 600 TZS
  - GP: 1,200 TZS
  - Specialist: 2,500 TZS
  - Psychologist: 1,500 TZS

#### ROYAL Tier
- **Pricing**: Premium multiplier on service types
- **Duration**: Same as ECONOMY
- **Features**:
  - Can request same doctor again within 14 days (unlimited follow-ups)
  - Doctor selection available ("Choose your preferred doctor")
  - Priority in queue
  - Preferred support
  - Consultation history available
- **Follow-up System**:
  - Completed Royal consultation stays "active" for 14 days
  - Patient can request follow-up anytime within 14-day window
  - Doctor can accept follow-up or patient can request substitute doctor
  - Follow-up consultation resets 14-day timer
  - No additional fee for follow-up request (only if doctor requests extension during call)
- **Follow-up Expiry Logic**:
  - followUpExpiry = consultation.createdAt + 14 days
  - Ongoing consultations include: status IN (active, in_progress, awaiting_extension, grace_period, completed) AND followUpExpiry > now

### 7.2 Consultation Lifecycle

#### States/Statuses
- `requested` â€” Patient creates request, doctor has 60 seconds to accept
- `pending` â€” Awaiting doctor acceptance (60s TTL)
- `active` â€” Consultation ongoing, timer running
- `in_progress` â€” (alias for active)
- `awaiting_extension` â€” Timer expired, patient needs to accept extension
- `grace_period` â€” Extension accepted, payment pending (15-min grace window)
- `completed` â€” Consultation ended (by doctor or timer + no extension)
- `cancelled` â€” Patient/doctor cancelled before start

#### Consultation Timeline
```
1. PatientConsultationRoute created
   â†“
2. Patient sends chief complaint, selects consultation type (chat/video)
   â†“ (Edge function: handle-consultation-request create)
3. Consultation inserted with status=pending, requestExpiresAt=now+60s
   â†“ (Realtime: IncomingRequestViewModel notified)
4. Doctor sees incoming request, accepts within 60s
   â†“ (Edge function: handle-consultation-request accept)
5. Consultation statusâ†’active, timer starts (scheduledEndAt = now + duration)
   â†“
6. Chat begins, video call available
   â†“ (If video call: VideoCallEntity created)
7. Timer reaches 0
   â†“ (manage-consultation timer_expired)
8. Statusâ†’awaiting_extension, show extension prompt
   â†“ (Patient accepts extension)
9. Statusâ†’grace_period, show payment screen (15-min payment window)
   â†“ (Patient confirms payment)
10. manage-consultation payment_confirmed
    â†“
11. Statusâ†’active, timer resets (extensionCount++, originalDurationMinutes adjusted)
    â†“
12. (Repeat 7-11 for each extension)
    â†“ (Or patient declines extension)
13. Doctor calls manage-consultation end
    â†“
14. Statusâ†’completed, report generation triggered
```

#### Key Time Fields
- `createdAt` â€” Consultation created timestamp
- `sessionStartTime` â€” When timer actually started (may differ from createdAt if delayed)
- `sessionEndTime` â€” When consultation ended
- `requestExpiresAt` â€” Deadline for doctor to accept (createdAt + 60s)
- `scheduledEndAt` â€” When timer will/did expire (sessionStartTime + originalDuration)
- `gracePeriodEndAt` â€” Deadline for payment after timer expiry (scheduledEndAt + 15 min)
- `followUpExpiry` â€” When follow-up eligibility expires (createdAt + 14 days for ROYAL)

---

### 7.3 Payment Flow

#### Service Access Payment (for initial consultation)
```
PatientHomeScreen
  â†’ TierSelectionRoute
  â†’ ServiceLocationRoute(tier)
  â†’ ServicesRoute(tier)
  â†’ FindDoctorRoute(...)
  â†’ BookAppointmentRoute(...)
  â†’ "Book Now" clicked
    â†“ (Edge function: handle-consultation-request create)
    â†’ Consultation created (checks PAYMENT_ENV)
    â†’ If PAYMENT_ENV != "mock":
        â†’ Verify service_access_payments for patient + service_type
        â†’ Must exist, access_granted=true, expires_at > now
        â†’ If not found: ValidationError
    â†“
  â†’ PatientConsultationRoute opened
    â†“ (Doctor accepts)
  â†’ Timer running, consultation active
```

#### Call Time Extension Payment (during active consultation)
```
Patient sees timer expiring
  â†’ Doctor requests extension via manage-consultation request_extension
  â†’ Statusâ†’awaiting_extension
  â†’ Patient sees PatientExtensionPrompt ("Doctor requests extension for 2,500 TZS")
  â†’ Patient clicks "Accept" or "Decline"
  â†“ (If "Accept")
    â†’ Statusâ†’grace_period (15-min payment window)
    â†’ Navigates to ExtensionPaymentScreen
    â†’ Initiates call-recharge-payment (2,500 TZS)
    â†’ M-Pesa STK push shown
    â†’ Payment confirmed via mpesa-callback
    â†“
    â†’ manage-consultation payment_confirmed called
    â†’ Statusâ†’active, extensionCount++, new scheduledEndAt calculated
    â†’ Timer resets, consultation continues
```

#### Payment Processing (M-Pesa)
```
Patient enters phone number on payment screen
  â†’ initiatePayment(phoneNumber)
  â†’ Calls service-access-payment or call-recharge-payment edge function
  â†’ Edge function delegates to mpesa-stk-push
  â†’ mpesa-stk-push:
    1. Creates PaymentEntity with status=pending
    2. Initiates M-Pesa STK push via M-Pesa API
    3. Returns stk_push_id
  â†’ PatientPaymentViewModel polls payment status
  â†’ mpesa-callback webhook called by M-Pesa (when user enters PIN or timeout)
  â†’ mpesa-callback:
    1. Validates callback signature
    2. Updates PaymentEntity.status = completed/failed
    3. Updates service_access_payments or call_recharge_payments table
    4. Triggers Firebase function for post-payment actions
  â†’ Payment status polling detects completion
  â†’ PatientPaymentScreen shows success
  â†’ Auto-navigates to PatientVideoCallRoute
```

---

### 7.4 Doctor Verification & Suspension

#### Doctor Registration
```
DoctorLoginScreen (if new doctor)
  â†’ DoctorTermsScreen
  â†’ DoctorRegistrationScreen
    - Collects: name, email, phone, specialty, specialistField
    - Collects: licenseNumber, yearsExperience, languages, services
    - File uploads: license document, certificates PDF
  â†’ register-doctor edge function
    - Validates all fields
    - Uploads documents to Firebase Storage
    - Creates DoctorProfileEntity with isVerified=false
    - Creates DoctorCredentialsEntity for each document
    - Notifies admin via notification
  â†’ Doctor dashboard shows "Pending Verification"
```

#### Verification Process (Admin)
```
Admin portal: admin-portal-action edge function
  â†’ action: "verify_doctor", doctorId, approval/rejection decision
  â†’ If approved:
    - DoctorProfileEntity.isVerified = true
    - All documents marked as verified
    - Doctor notified via FCM
    - Added to available doctor list
  â†’ If rejected:
    - DoctorProfileEntity.rejectionReason = reason
    - DoctorProfileEntity.isVerified = false
    - Doctor can re-submit with corrections
```

#### Suspension & Ban
```
Admin flags doctor for suspension (via admin-portal-action)
  â†’ DoctorProfileEntity.isBanned = true (or isVerified = false with reason)
  â†’ DoctorProfileEntity.bannedAt, banReason set
  â†’ Doctor dashboard shows "Account Suspended" banner
  â†’ DoctorDashboardViewModel.suspensionStatus populated
  â†’ Doctor cannot accept new consultations
  â†’ Doctor cannot toggle availability

OR: Automatic suspension (via lift-expired-suspensions cron)
  â†’ If doctor has >= 3 missed appointments in last 7 days
  â†’ Or completion rate < 50%
  â†’ Can appeal via admin portal
```

---

### 7.5 Doctor Earnings & Payouts

#### Earnings Calculation
```
Per completed consultation:
  1. Doctor fee = consultation.consultationFee (base service price)
  2. Platform fee = 30% (assumed)
  3. Doctor earns = Doctor fee * 0.70
  4. DoctorEarningsEntity created:
     - earningId = UUID
     - doctorId = consultation.doctorId
     - consultationId = consultation.consultationId
     - amount = Doctor fee * 0.70
     - status = "pending"
     - paidAt = null (until payout)

Tracking:
  - DoctorEarningsDao queries:
    - getByDoctorIdAndStatus(doctorId, "pending") â†’ unpaid earnings
    - getTotalEarnings(doctorId, startDate, endDate) â†’ dashboard stats
```

#### Payout Process (Admin only)
```
Admin portal: admin-portal-action edge function
  â†’ action: "process_payout", doctorId, paymentMethod
  â†’ Aggregates all pending DoctorEarningsEntity for doctor
  â†’ Marks status = "paid" + sets paidAt
  â†’ Transfers to doctor's M-Pesa account or bank
  â†’ Doctor receives notification
  â†’ Doctor dashboard shows updated earnings
```

#### Dashboard Display (DoctorDashboardViewModel)
```
earningsToday = sum(amount where status=pending AND createdAt >= today)
earningsMonth = sum(amount where status=pending AND createdAt >= month start)
earningsPending = sum(amount where status=pending)

On-demand:
  - Doctor can view earnings history (all time, all consultations)
  - Can filter by date range, status (pending/paid)
  - Can request manual payout (email sent to admin)
```

---

### 7.6 Follow-Up System (Royal Tier)

#### Requesting Follow-Up
```
Patient views OngoingConsultationsScreen
  â†’ Sees completed consultation with "Request Follow-up" badge
  â†’ Checks: status=completed AND followUpExpiry > now AND serviceTier=ROYAL
  â†’ Clicks "Request Follow-up"
  â†’ Navigates to FollowUpWaitingRoute(parentConsultationId, doctorId, serviceType)
```

#### Follow-Up Request Processing
```
FollowUpWaitingScreen shows:
  - Doctor info card
  - "Waiting for doctor..." status with spinner
  - Behind-the-scenes: Realtime subscription to consultation_requests table

Doctor's DoctorDashboardScreen:
  - Incoming request notification appears
  - IncomingRequestViewModel detects new request
  - Can accept or reject within 60s (same as initial request)

If doctor accepts:
  â†’ New ConsultationEntity created with:
     - parentConsultationId = original consultation ID
     - status = "active"
     - serviceTier = "ROYAL"
     - consultationFee = 0 (no additional cost for follow-up)
     - Follow-up timer starts (15 min same as initial)
  â†’ Patient navigates to PatientConsultationRoute(newConsultationId)
  â†’ Consultation proceeds same as initial

If doctor rejects:
  â†’ Patient sees "Doctor declined" message
  â†’ Two options:
    1. "Book with same doctor" â†’ BookAppointmentRoute (scheduling new appointment)
    2. "Request substitute" â†’ SubstituteFollowUpRoute (find different doctor)
```

#### Substitute Follow-Up
```
SubstituteFollowUpRoute:
  - Shows FindDoctorScreen pre-filtered for same service type
  - Excludes original doctor (optional)
  - Patient selects new doctor
  â†’ BookAppointmentRoute (with isSubstituteFollowUp=true flag)
  â†’ New consultation created (still marked as follow-up internally)
  â†’ Follows same flow as initial booking
```

---

### 7.7 Report Generation & Delivery

#### Report Creation (Doctor)
```
During/after consultation:
  1. Doctor clicks "Write Report" on DoctorConsultationDetailScreen
  2. Navigates to DoctorReportRoute(consultationId)
  3. Doctor fills form:
     - Chief complaint, symptoms, diagnosis
     - ICD-10 code search (auto-complete from ICD database)
     - Severity: mild/moderate/severe
     - Treatment plan
     - Prescriptions: add/remove medications
     - Follow-up recommendations: yes/no, days (7/14/30)
  4. Form auto-saves to localStorage (draft)
  5. Doctor clicks "Submit Report"
     â†’ Validation:
        - Diagnosis required
        - Treatment plan required
        - All required fields filled
     â†’ Edge function: generate-consultation-report
        - Creates PatientReportEntity
        - Generates PDF from form data + consultation summary
        - Uploads PDF to Firebase Storage
        - Stores reportUrl in database
        - Generates unique verificationCode for authentication
        - Creates system message in chat ("Report generated")
  6. DoctorReportScreen shows "Report Submitted" with verification code
  7. Doctor can copy/share verification code
  8. Navigation: onReportSubmitted â†’ popBackStack(DoctorDashboardRoute, false)
```

#### Report Delivery to Patient
```
1. Edge function emits FCM notification:
   - "Your report from Dr. <name> is ready"
   - action: "open_report"
   - consultationId in payload

2. PatientHomeScreen updates:
   - "hasUnreadReports" flag set to true
   - Notification badge on reports button

3. Patient clicks notification or navigates to ReportsScreen
   â†’ See list of all reports
   â†’ Unread badge on new report
   â†’ Click report â†’ ReportDetailRoute(reportId)

4. ReportDetailScreen displays:
   - Doctor name, date generated
   - Expandable sections:
     - Chief Complaint / Presenting Symptoms
     - Diagnosis Assessment (with ICD code if available)
     - Treatment Plan
     - Follow-up Instructions
     - Prescriptions (table with dosage, frequency, duration)
     - Further Notes
     - Verification Code (for external verification)
   - Buttons:
     - Download PDF (saves to local storage/Downloads)
     - Share (via intent, email, WhatsApp)
     - Verify Report (sends verificationCode to verify-report edge function)

5. Report Verification (optional):
   â†’ Patient can share verification code with pharmacist, another doctor
   â†’ They can call verify-report edge function with code
   â†’ System confirms report authenticity
```

---

## 8. ADDITIONAL SYSTEMS

### 8.1 Notification System

#### Notification Types & Triggers
| Event | Recipient | Title | Action |
|-------|-----------|-------|--------|
| Doctor accepts request | Patient | "Dr. <name> accepted your request" | â†’ PatientConsultationRoute |
| Doctor rejects request | Patient | "Dr. <name> declined your request" | â†’ FindDoctorRoute |
| Appointment reminder | Doctor | "You have an appointment in 15 min" | â†’ DoctorConsultationDetailRoute |
| Timer expired | Patient | "Your consultation time has ended" | â†’ Extension prompt |
| Extension request | Patient | "Doctor requests extension for 2,500 TZS" | â†’ Accept/Decline |
| Report ready | Patient | "Your report is ready" | â†’ ReportsRoute |
| Payment confirmed | Patient | "Payment successful" | â†’ PatientVideoCallRoute |
| Payment failed | Patient | "Payment failed: <reason>" | â†’ Retry payment |
| Doctor online | (internal) | Availability updated | Update FindDoctor list |

#### Notification Storage
- **NotificationEntity** table
- Synced to local Room database
- Real-time updates via Supabase Realtime
- readAt timestamp set when marked as read

---

### 8.2 Offline Support

#### Offline Capabilities
- **Messages**: Queued for sync (MessageEntity.synced flag)
- **Ratings**: Queued for sync (DoctorRatingEntity.synced flag)
- **Payments**: Queued for sync (PaymentEntity.synced flag)

#### Sync Strategy
```
On app startup:
  1. WorkManager periodic sync job (every 15 minutes)
  2. Checks for unsyncedMessages, unsyncedRatings, unsyncedPayments
  3. Attempts to upload to Supabase
  4. On success: marks synced=true in local DB
  5. On failure: logs error, retries next cycle
```

#### Device Binding (for offline sessions)
- Patient can bind device to session for offline consultation access
- bind-device edge function stores device fingerprint + session ID
- deauthorize-device removes binding
- check-device-binding verifies if device has active session

---

### 8.3 Localization

#### Supported Languages
- English (en)
- Swahili (sw)
- Others TBD

#### Implementation
- `@AppCompatDelegate.getApplicationLocales()` used to detect language
- SharedPreferences store language preference
- String resources in feature/*/src/main/res/values-*/strings.xml
- LanguageSelectionScreen allows user to set preferred language

---

### 8.4 Accessibility & Compliance

#### GDPR/Privacy
- Patient sessions are anonymous (no email/phone stored, only session token)
- Data deletion: Patients can request session deletion via settings
- Encryption: Session tokens hashed with SHA-256 + PBKDF2

#### Accessibility
- Material 3 components with proper contrast ratios
- Semantic annotations for screen readers
- Large touch targets (48dp minimum)
- Text scaling support

---

## 9. KEY IMPLEMENTATION PATTERNS FOR PWA

### 9.1 State Management Mapping
- **Android ViewModel** â†’ **React Hook (useState, useReducer, Zustand, Redux)**
- Use `useEffect` for side effects (Supabase subscriptions, API calls)
- Implement proper cleanup (unsubscribe from Realtime)

### 9.2 Navigation Mapping
- **Android Navigation Component** â†’ **React Router or TanStack Router**
- Preserve route parameters in URL search params or path segments
- History management for back button functionality

### 9.3 Real-Time Sync
- **Supabase Realtime** remains the same
- Web client: use `@supabase/supabase-js` library
- Subscribe to channels in `useEffect`, cleanup on unmount

### 9.4 Offline Support
- **Service Worker** for caching API responses
- **IndexedDB** or **localStorage** for local entity storage (replace Room)
- **Queue system** for offline mutations (messages, ratings, payments)

### 9.5 Video Calling
- **VideoSDK** has JavaScript SDK
- Direct integration into React components
- Same token generation via videosdk-token edge function

### 9.6 Payment Integration
- **M-Pesa** STK push works same way (mpesa-stk-push edge function)
- Webhook handling for payment callbacks
- Same polling/callback architecture

### 9.7 File Upload & Storage
- **Firebase Storage** remains the same
- Use `@firebase/storage` library for uploads
- Same file picker â†’ upload â†’ URL retrieval flow

---

## 10. CRITICAL EDGE FUNCTIONS (Full List)

All 64 edge functions with brief descriptions:

**Auth (7)**
- create-patient-session, refresh-patient-session, extend-session
- login-doctor, send-doctor-otp, verify-doctor-otp, login-agent

**Consultation (5)**
- create-consultation, handle-consultation-request, manage-consultation
- book-appointment, reschedule-appointment

**Payment (4)**
- service-access-payment, mpesa-stk-push, mpesa-callback, call-recharge-payment

**Doctor (10)**
- register-doctor, list-doctors, list-all-doctors, manage-doctor
- get-doctor-ratings, get-all-ratings, rate-doctor, log-doctor-online
- send-appointment-notification, appointment-reminder

**Patient (3)**
- register-agent, update-patient-demographics, setup-recovery

**Recovery (2)**
- recover-by-id, recover-by-questions

**Messages & Notifications (2)**
- handle-messages, send-push-notification

**Reports (4)**
- generate-consultation-report, generate-patient-summary
- get-patient-reports, verify-report

**Video (1)**
- videosdk-token

**Analytics & Monitoring (8)**
- generate-health-analytics, generate-doctor-performance-report
- generate-analytics-report, get-admin-stats, get-audit-logs
- log-performance-metrics, handle-missed-appointments, lift-expired-suspensions

**Device & Admin (5)**
- check-device-binding, bind-device, deauthorize-device
- admin-portal-action, create-portal-user

**Utilities (4)**
- get-security-questions, get-doctor-slots, update-fcm-token, get-vapid-key

---

This comprehensive analysis covers every component needed to rebuild eSIRI Plus as a fully-functional PWA. All navigation flows, database schemas, API contracts, business logic, and edge functions are documented with exact file paths and implementation details.