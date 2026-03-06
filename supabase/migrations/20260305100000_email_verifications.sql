-- Email OTP verification for doctor registration
CREATE TABLE IF NOT EXISTS public.email_verifications (
    id uuid DEFAULT gen_random_uuid() PRIMARY KEY,
    email text NOT NULL,
    otp_code text NOT NULL,
    expires_at timestamptz NOT NULL,
    attempts int DEFAULT 0,
    verified_at timestamptz,
    created_at timestamptz DEFAULT now()
);

-- Index for looking up latest OTP by email
CREATE INDEX idx_email_verifications_email ON public.email_verifications (email, created_at DESC);

-- RLS: only service role can access
ALTER TABLE public.email_verifications ENABLE ROW LEVEL SECURITY;

-- No public policies — only service_role can read/write
