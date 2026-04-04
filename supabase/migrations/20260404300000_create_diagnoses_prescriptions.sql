CREATE TABLE IF NOT EXISTS public.diagnoses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    consultation_id UUID REFERENCES public.consultations(consultation_id) ON DELETE CASCADE,
    icd_code TEXT,
    description TEXT,
    severity TEXT,
    created_at TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE public.diagnoses ENABLE ROW LEVEL SECURITY;

DO $$ BEGIN
  CREATE POLICY "Portal users can read diagnoses"
    ON public.diagnoses FOR SELECT TO authenticated
    USING (public.is_portal_user());
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

CREATE TABLE IF NOT EXISTS public.prescriptions (
    prescription_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    consultation_id UUID REFERENCES public.consultations(consultation_id) ON DELETE CASCADE,
    medication_name TEXT NOT NULL,
    dosage TEXT,
    frequency TEXT,
    duration TEXT,
    created_at TIMESTAMPTZ DEFAULT now()
);

ALTER TABLE public.prescriptions ENABLE ROW LEVEL SECURITY;

DO $$ BEGIN
  CREATE POLICY "Portal users can read prescriptions"
    ON public.prescriptions FOR SELECT TO authenticated
    USING (public.is_portal_user());
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

NOTIFY pgrst, 'reload schema';
