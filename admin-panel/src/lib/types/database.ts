export type PortalRole = "admin" | "hr" | "finance" | "audit";

export interface UserRole {
  user_id: string;
  role_name: PortalRole;
  created_at: string;
}

export interface DoctorProfile {
  doctor_id: string;
  full_name: string;
  email: string;
  phone: string;
  specialty: string;
  specialist_field: string | null;
  languages: string[];
  bio: string;
  license_number: string;
  years_experience: number;
  profile_photo_url: string | null;
  average_rating: number;
  total_ratings: number;
  is_verified: boolean;
  is_available: boolean;
  created_at: string;
  updated_at: string;
  services: string | null;
  country_code: string | null;
  country: string | null;
  license_document_url: string | null;
  certificates_url: string | null;
  rejection_reason: string | null;
  suspended_until: string | null;
  suspension_reason: string | null;
  is_banned: boolean;
  banned_at: string | null;
  ban_reason: string | null;
  verification_status: string | null;
  warning_message: string | null;
  warning_at: string | null;
}

export interface Consultation {
  consultation_id: string;
  patient_session_id: string;
  doctor_id: string;
  status: string;
  service_type: string;
  consultation_fee: number;
  session_start_time: string | null;
  session_end_time: string | null;
  session_duration_minutes: number;
  created_at: string;
  updated_at: string;
}

export interface Payment {
  payment_id: string;
  consultation_id: string;
  amount: number;
  currency: string;
  payment_method: string;
  status: string;
  created_at: string;
}

export interface ServiceAccessPayment {
  id: string;
  session_id: string;
  tier_id: string;
  amount: number;
  currency: string;
  payment_method: string;
  status: string;
  transaction_ref: string | null;
  created_at: string;
}

export interface AdminLog {
  id: string;
  admin_id: string;
  action: string;
  target_type: string | null;
  target_id: string | null;
  details: Record<string, unknown> | null;
  created_at: string;
}

export interface DoctorDeviceBinding {
  id: string;
  doctor_id: string;
  device_fingerprint: string;
  bound_at: string;
  is_active: boolean;
}

export interface SupabaseUser {
  id: string;
  email: string;
  created_at: string;
  last_sign_in_at: string | null;
  banned_until: string | null;
  user_metadata: {
    full_name?: string;
    [key: string]: unknown;
  };
}

export interface AdminLogRow {
  id?: string;
  log_id?: string;
  admin_id: string | null;
  action: string;
  target_type: string | null;
  target_id: string | null;
  details: Record<string, unknown> | null;
  level: string | null;
  function_name: string | null;
  ip_address: string | null;
  error_message: string | null;
  metadata: Record<string, unknown> | null;
  created_at: string;
}

export interface AgentProfile {
  id: string;
  agent_id: string;
  full_name: string;
  mobile_number: string;
  email: string;
  place_of_residence: string;
  is_active: boolean;
  created_at: string;
  updated_at: string;
}

export interface PerformanceStat {
  bucket: string;
  metric_type: string;
  endpoint: string;
  avg_latency_ms: number;
  p95_latency_ms: number;
  request_count: number;
  error_count: number;
}

export interface RiskFlag {
  flag_id: string;
  doctor_id: string;
  flag_type: string;
  severity: string;
  title: string;
  description: string | null;
  is_resolved: boolean;
  created_at: string;
  doctor_profiles: { full_name: string } | null;
}
