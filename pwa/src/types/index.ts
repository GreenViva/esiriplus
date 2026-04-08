// ── User & Auth ──────────────────────────────────────────────────────────────

export type UserRole = 'patient' | 'doctor' | 'agent';

export interface User {
  id: string;
  fullName: string;
  phone: string;
  email?: string;
  role: UserRole;
  isVerified: boolean;
}

export interface Session {
  accessToken: string;
  refreshToken: string;
  expiresAt: number;
  user: User;
}

export interface PatientSession {
  sessionId: string;
  ageGroup?: string;
  sex?: string;
  region?: string;
  bloodType?: string;
  allergies: string[];
  chronicConditions: string[];
}

// ── Consultation ─────────────────────────────────────────────────────────────

export type ConsultationStatus =
  | 'pending'
  | 'active'
  | 'in_progress'
  | 'awaiting_extension'
  | 'grace_period'
  | 'completed'
  | 'cancelled'
  | 'expired';

export type ServiceTier = 'ECONOMY' | 'ROYAL';

export interface Consultation {
  consultation_id: string;
  patient_session_id: string;
  doctor_id: string;
  status: ConsultationStatus;
  service_type: string;
  service_tier: ServiceTier;
  consultation_fee: number;
  session_start_time?: string;
  session_end_time?: string;
  session_duration_minutes: number;
  scheduled_end_at?: string;
  extension_count: number;
  grace_period_end_at?: string;
  follow_up_expiry?: string;
  is_premium: boolean;
  parent_consultation_id?: string;
  created_at: string;
  updated_at: string;
}

// ── Doctor ────────────────────────────────────────────────────────────────────

export interface DoctorProfile {
  doctorId: string;
  fullName: string;
  email: string;
  phone: string;
  specialty: string;
  specialistField?: string;
  licenseNumber: string;
  yearsExperience: number;
  bio: string;
  isVerified: boolean;
  isAvailable: boolean;
  isOnline: boolean;
  rating: number;
  totalConsultations: number;
  profilePhotoUrl?: string;
  languages: string[];
  services: string[];
  country: string;
  // Warning fields
  warningMessage?: string;
  warningAt?: string;
  warningCount?: number;
  warningAcknowledged?: boolean;
  // Status fields
  rejectionReason?: string;
  suspendedUntil?: string;
  suspensionReason?: string;
  isBanned?: boolean;
  bannedAt?: string;
  banReason?: string;
  // Session & capability flags
  inSession?: boolean;
  canServeAsGp?: boolean;
  reportSubmitted?: boolean;
}

/** Raw doctor_profiles row from Supabase (snake_case) */
export interface DoctorProfileRow {
  doctor_id: string;
  full_name: string;
  email: string;
  phone: string;
  specialty: string;
  specialist_field?: string;
  license_number: string;
  years_experience: number;
  bio: string;
  is_verified: boolean;
  is_available: boolean;
  average_rating: number;
  total_ratings: number;
  profile_photo_url?: string;
  languages: string[];
  services: string[];
  country: string;
  country_code?: string;
  // Warning
  warning_message?: string;
  warning_at?: string;
  warning_count: number;
  warning_acknowledged: boolean;
  // Status
  rejection_reason?: string;
  suspended_until?: string;
  suspension_reason?: string;
  is_banned: boolean;
  banned_at?: string;
  ban_reason?: string;
  // Session
  in_session: boolean;
  can_serve_as_gp: boolean;
  report_submitted: boolean;
  created_at: string;
  updated_at: string;
}

export interface DoctorAvailability {
  dayOfWeek: string;
  enabled: boolean;
  startTime: string;
  endTime: string;
}

export interface DoctorEarnings {
  totalEarnings: number;
  thisMonthEarnings: number;
  lastMonthEarnings: number;
  pendingPayout: number;
  transactions: EarningsTransaction[];
}

export interface EarningsTransaction {
  id: string;
  patientName: string;
  amount: number;
  date: string;
  status: string;
}

/** Raw doctor_earnings row from Supabase (snake_case) */
export interface EarningsRow {
  earning_id?: string;
  id?: string;
  doctor_id: string;
  consultation_id: string;
  amount: number;
  status: 'pending' | 'paid' | 'cancelled';
  earning_type: 'consultation' | 'follow_up' | 'substitute_follow_up' | 'substitute_consultation';
  paid_at?: string;
  created_at: string;
}

// ── Messages & Chat ──────────────────────────────────────────────────────────

export interface Message {
  message_id: string;
  consultation_id: string;
  sender_id: string;
  sender_type: 'patient' | 'doctor' | 'system';
  message_text: string;
  message_type: 'text' | 'image' | 'file' | 'document';
  attachment_url?: string;
  is_read?: boolean;
  is_from_previous_session?: boolean;
  created_at: string;
}

export interface Attachment {
  attachmentId: string;
  messageId: string;
  fileName: string;
  fileUrl: string;
  fileType: string;
  fileSize: number;
}

// ── Prescription & Report ────────────────────────────────────────────────────

export interface Prescription {
  medication: string;
  form: 'Tablets' | 'Syrup' | 'Injection';
  quantity: number;
  timesPerDay: number;
  days: number;
  route?: string; // IM, IV, SC for injections
}

export interface PatientReport {
  reportId: string;
  consultationId: string;
  doctorName: string;
  presentingSymptoms: string;
  diagnosedProblem: string;
  category: string;
  severity: string;
  assessment: string;
  treatmentPlan: string;
  followUpPlan: string;
  furtherNotes?: string;
  followUpRecommended: boolean;
  prescriptions: Prescription[];
  consultationDate: string;
  createdAt: number;
}

// ── Consultation Request ────────────────────────────────────────────────────

export type ConsultationRequestStatus = 'pending' | 'accepted' | 'rejected' | 'expired';

export interface ConsultationRequest {
  request_id: string;
  patient_session_id: string;
  doctor_id: string;
  service_type: string;
  service_tier?: string;
  consultation_type?: string;
  chief_complaint?: string;
  symptoms?: string;
  patient_age_group?: string;
  patient_sex?: string;
  patient_blood_group?: string;
  patient_allergies?: string;
  patient_chronic_conditions?: string;
  status: ConsultationRequestStatus;
  consultation_id?: string;
  created_at: string;
  expires_at: string;
}

export interface IncomingRequestState {
  requestId: string | null;
  patientSessionId: string | null;
  serviceType: string | null;
  serviceTier: string | null;
  chiefComplaint: string | null;
  isFollowUp: boolean;
  isSubstituteFollowUp: boolean;
  secondsRemaining: number;
  isResponding: boolean;
  responseStatus: ConsultationRequestStatus | null;
  errorMessage: string | null;
  canRetry: boolean;
  consultationId: string | null;
  // Patient health info
  symptoms: string | null;
  patientAgeGroup: string | null;
  patientSex: string | null;
  patientBloodGroup: string | null;
  patientAllergies: string | null;
  patientChronicConditions: string | null;
}

// ── Video Call ────────────────────────────────────────────────────────────────

export type CallType = 'AUDIO' | 'VIDEO';

export interface VideoCallToken {
  token: string;
  roomId: string;
  permissions: string[];
  expiresIn: number;
}

export interface IncomingCall {
  consultationId: string;
  roomId: string;
  callType: CallType;
  callerRole: 'doctor' | 'patient';
  timestamp: number;
}

// ── Payment ──────────────────────────────────────────────────────────────────

export interface Payment {
  paymentId: string;
  consultationId: string;
  amount: number;
  currency: string;
  status: 'pending' | 'completed' | 'failed';
  method: string;
  createdAt: number;
}

// ── Notification ─────────────────────────────────────────────────────────────

export interface AppNotification {
  notificationId: string;
  title: string;
  body: string;
  type: string;
  isRead: boolean;
  createdAt: number;
}

// ── Appointment ──────────────────────────────────────────────────────────────

export interface Appointment {
  id: string;
  consultationId: string;
  patientSessionId: string;
  doctorId: string;
  scheduledAt: number;
  serviceType: string;
  status: string;
}
