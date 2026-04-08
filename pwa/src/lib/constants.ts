// Brand colors
export const BRAND_TEAL = '#2A9D8F';
export const ROYAL_PURPLE = '#4C1D95';
export const ROYAL_GOLD = '#F59E0B';
export const AGENT_ORANGE = '#EF6C00';
export const SUCCESS_GREEN = '#16A34A';
export const ERROR_RED = '#DC2626';
export const SUBTITLE_GREY = '#6B7280';
export const CARD_BORDER = '#E5E7EB';

// Service tiers
export const SERVICE_TIERS = {
  ECONOMY: {
    name: 'Economy',
    badge: 'STANDARD',
    duration: 15,
    followUpCount: 1,
    color: BRAND_TEAL,
  },
  ROYAL: {
    name: 'Royal',
    badge: '\u2605 PREMIUM',
    duration: 15,
    followUpDays: 14,
    color: ROYAL_PURPLE,
  },
} as const;

// Consultation statuses
export const CONSULTATION_STATUSES = {
  PENDING: 'pending',
  ACTIVE: 'active',
  IN_PROGRESS: 'in_progress',
  AWAITING_EXTENSION: 'awaiting_extension',
  GRACE_PERIOD: 'grace_period',
  COMPLETED: 'completed',
  CANCELLED: 'cancelled',
  EXPIRED: 'expired',
} as const;

// Severity levels
export const SEVERITIES = ['Mild', 'Moderate', 'Severe'] as const;

// Report categories
export const CATEGORIES = [
  'General Medicine',
  'Neurological Conditions',
  'Cardiovascular',
  'Respiratory',
  'Gastrointestinal',
  'Musculoskeletal',
  'Dermatological',
  'Mental Health',
  'Infectious Disease',
  'Other',
] as const;

// Currency
export const CURRENCY = 'TZS';
export const CURRENCY_SYMBOL = 'TSh';
