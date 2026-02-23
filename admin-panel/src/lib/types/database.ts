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
