-- Doctor device bindings: one doctor ↔ one device enforced by UNIQUE constraints
CREATE TABLE public.doctor_device_bindings (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    doctor_id UUID NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    device_fingerprint TEXT NOT NULL,
    bound_at TIMESTAMPTZ DEFAULT now(),
    is_active BOOLEAN DEFAULT true,
    CONSTRAINT unique_doctor_binding UNIQUE (doctor_id),
    CONSTRAINT unique_device_binding UNIQUE (device_fingerprint)
);

-- RLS: doctors can read their own binding; service role can manage all
ALTER TABLE public.doctor_device_bindings ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Doctors can view own binding"
    ON public.doctor_device_bindings
    FOR SELECT
    USING (auth.uid() = doctor_id);

CREATE POLICY "Service role full access"
    ON public.doctor_device_bindings
    FOR ALL
    USING (auth.role() = 'service_role');
