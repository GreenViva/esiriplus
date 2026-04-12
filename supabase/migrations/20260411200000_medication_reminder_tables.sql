-- ============================================================================
-- Medication Reminder System for Royal Patients
--
-- Nurses call Royal-tier patients at scheduled times to remind them
-- to take their medication. Doctors create the timetable during report
-- submission; a cron job matches due times and auto-assigns nurses.
-- ============================================================================

-- ── 1. Medication Timetables ─────────────────────────────────────────────────
-- One row per medication per consultation. The doctor sets the schedule
-- (e.g., Amoxicillin 3x/day at 08:00, 14:00, 20:00 for 7 days).

CREATE TABLE IF NOT EXISTS public.medication_timetables (
    timetable_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    consultation_id    UUID NOT NULL REFERENCES public.consultations(consultation_id) ON DELETE CASCADE,
    patient_session_id TEXT NOT NULL,
    doctor_id          UUID NOT NULL,
    medication_name    TEXT NOT NULL,
    dosage             TEXT,                              -- e.g. "500mg", "5ml"
    form               TEXT DEFAULT 'Tablets',            -- Tablets, Syrup, Injection
    times_per_day      INT NOT NULL CHECK (times_per_day BETWEEN 1 AND 6),
    scheduled_times    TEXT[] NOT NULL,                   -- e.g. {'08:00','14:00','20:00'} in EAT
    duration_days      INT NOT NULL CHECK (duration_days >= 1),
    start_date         DATE NOT NULL DEFAULT CURRENT_DATE,
    end_date           DATE NOT NULL,                    -- computed: start_date + duration_days - 1
    is_active          BOOLEAN NOT NULL DEFAULT true,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Fast lookups by the cron job
CREATE INDEX IF NOT EXISTS idx_med_timetable_active
    ON medication_timetables (is_active, start_date, end_date)
    WHERE is_active = true;

CREATE INDEX IF NOT EXISTS idx_med_timetable_patient
    ON medication_timetables (patient_session_id);

CREATE INDEX IF NOT EXISTS idx_med_timetable_consultation
    ON medication_timetables (consultation_id);

-- ── 2. Medication Reminder Events ────────────────────────────────────────────
-- One row per scheduled reminder occurrence. The UNIQUE constraint
-- prevents the cron from creating duplicate events for the same time slot.

CREATE TABLE IF NOT EXISTS public.medication_reminder_events (
    event_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    timetable_id       UUID NOT NULL REFERENCES public.medication_timetables(timetable_id) ON DELETE CASCADE,
    scheduled_date     DATE NOT NULL,
    scheduled_time     TEXT NOT NULL,                     -- 'HH:MM' in EAT
    status             TEXT NOT NULL DEFAULT 'pending'
                       CHECK (status IN ('pending','nurse_notified','nurse_calling','completed','no_nurse','patient_unreachable','failed')),
    nurse_id           UUID,                              -- assigned nurse (doctor_profiles.doctor_id)
    video_room_id      TEXT,                              -- VideoSDK room for the reminder call
    nurse_notified_at  TIMESTAMPTZ,
    call_started_at    TIMESTAMPTZ,
    call_ended_at      TIMESTAMPTZ,
    retry_count        INT NOT NULL DEFAULT 0,
    reassign_count     INT NOT NULL DEFAULT 0,
    patient_notified   BOOLEAN NOT NULL DEFAULT false,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Duplicate prevention: one event per timetable + date + time
    UNIQUE (timetable_id, scheduled_date, scheduled_time)
);

CREATE INDEX IF NOT EXISTS idx_med_events_pending
    ON medication_reminder_events (status, scheduled_date, scheduled_time)
    WHERE status IN ('pending', 'no_nurse', 'nurse_notified');

CREATE INDEX IF NOT EXISTS idx_med_events_nurse
    ON medication_reminder_events (nurse_id)
    WHERE nurse_id IS NOT NULL;

-- ── 3. RLS Policies ──────────────────────────────────────────────────────────

ALTER TABLE medication_timetables ENABLE ROW LEVEL SECURITY;
ALTER TABLE medication_reminder_events ENABLE ROW LEVEL SECURITY;

-- Doctors see timetables they created
CREATE POLICY "doctors_read_own_timetables"
    ON medication_timetables FOR SELECT TO authenticated
    USING (doctor_id = auth.uid());

-- Patients see their own timetables
CREATE POLICY "patients_read_own_timetables"
    ON medication_timetables FOR SELECT TO authenticated
    USING (patient_session_id = auth.uid()::text);

-- Service role has full access (edge functions)
CREATE POLICY "service_role_all_timetables"
    ON medication_timetables FOR ALL TO service_role
    USING (true) WITH CHECK (true);

-- Events: doctors (nurses) see events assigned to them
CREATE POLICY "nurses_read_assigned_events"
    ON medication_reminder_events FOR SELECT TO authenticated
    USING (nurse_id = auth.uid());

-- Events: patients see their own via timetable join
CREATE POLICY "patients_read_own_events"
    ON medication_reminder_events FOR SELECT TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM medication_timetables t
            WHERE t.timetable_id = medication_reminder_events.timetable_id
              AND t.patient_session_id = auth.uid()::text
        )
    );

-- Service role full access on events
CREATE POLICY "service_role_all_events"
    ON medication_reminder_events FOR ALL TO service_role
    USING (true) WITH CHECK (true);

-- ── 4. Realtime ──────────────────────────────────────────────────────────────

ALTER PUBLICATION supabase_realtime ADD TABLE medication_timetables;
ALTER PUBLICATION supabase_realtime ADD TABLE medication_reminder_events;
