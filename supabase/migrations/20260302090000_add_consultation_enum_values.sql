-- Add new consultation status enum values.
-- This MUST be in a separate migration from any code that references
-- these values, because PostgreSQL does not allow using newly-added
-- enum values in the same transaction.
ALTER TYPE consultation_status_enum ADD VALUE IF NOT EXISTS 'awaiting_extension';
ALTER TYPE consultation_status_enum ADD VALUE IF NOT EXISTS 'grace_period';
