-- Migration: Atomic booking RPCs with advisory locks to prevent race conditions

-- ============================================================================
-- book_appointment: Atomically validate + insert an appointment
-- ============================================================================
CREATE OR REPLACE FUNCTION book_appointment(
  p_doctor_id           UUID,
  p_patient_session_id  UUID,
  p_scheduled_at        TIMESTAMPTZ,
  p_duration_minutes    SMALLINT DEFAULT 15,
  p_service_type        TEXT DEFAULT 'gp',
  p_consultation_type   TEXT DEFAULT 'chat',
  p_chief_complaint     TEXT DEFAULT '',
  p_consultation_fee    INTEGER DEFAULT 0,
  p_grace_period_minutes SMALLINT DEFAULT 5
)
RETURNS TABLE (
  appointment_id UUID,
  doctor_id      UUID,
  scheduled_at   TIMESTAMPTZ,
  status         TEXT,
  created_at     TIMESTAMPTZ
)
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  v_lock_key BIGINT;
  v_day_of_week SMALLINT;
  v_time_of_day TIME;
  v_slot_end TIME;
  v_daily_count INTEGER;
  v_max_per_day SMALLINT;
  v_new_id UUID;
BEGIN
  -- Advisory lock keyed on doctor_id to serialize concurrent bookings
  v_lock_key := ('x' || left(md5(p_doctor_id::text), 15))::bit(60)::bigint;
  PERFORM pg_advisory_xact_lock(v_lock_key);

  -- 1. Validate: scheduled_at must be in the future
  IF p_scheduled_at <= now() THEN
    RAISE EXCEPTION 'Cannot book appointments in the past';
  END IF;

  -- 2. Validate: doctor has an availability slot covering this time
  v_day_of_week := EXTRACT(DOW FROM p_scheduled_at AT TIME ZONE 'Africa/Nairobi')::SMALLINT;
  v_time_of_day := (p_scheduled_at AT TIME ZONE 'Africa/Nairobi')::TIME;

  SELECT das.end_time INTO v_slot_end
  FROM doctor_availability_slots das
  WHERE das.doctor_id = p_doctor_id
    AND das.day_of_week = v_day_of_week
    AND das.is_active = true
    AND das.start_time <= v_time_of_day
    AND das.end_time > v_time_of_day
  LIMIT 1;

  IF v_slot_end IS NULL THEN
    RAISE EXCEPTION 'Doctor is not available at this time';
  END IF;

  -- 3. Check for time overlap with existing active appointments
  IF EXISTS (
    SELECT 1 FROM appointments a
    WHERE a.doctor_id = p_doctor_id
      AND a.status IN ('booked', 'confirmed', 'in_progress')
      AND tstzrange(a.scheduled_at, a.scheduled_at + (a.duration_minutes || ' minutes')::interval)
          && tstzrange(p_scheduled_at, p_scheduled_at + (p_duration_minutes || ' minutes')::interval)
  ) THEN
    RAISE EXCEPTION 'Time slot conflicts with an existing appointment';
  END IF;

  -- 4. Check daily appointment limit
  SELECT max_appointments_per_day INTO v_max_per_day
  FROM doctor_profiles
  WHERE doctor_profiles.doctor_id = p_doctor_id;

  v_max_per_day := COALESCE(v_max_per_day, 10);

  SELECT COUNT(*) INTO v_daily_count
  FROM appointments a
  WHERE a.doctor_id = p_doctor_id
    AND a.status IN ('booked', 'confirmed', 'in_progress', 'completed')
    AND (a.scheduled_at AT TIME ZONE 'Africa/Nairobi')::date
        = (p_scheduled_at AT TIME ZONE 'Africa/Nairobi')::date;

  IF v_daily_count >= v_max_per_day THEN
    RAISE EXCEPTION 'Doctor has reached the maximum appointments for this day';
  END IF;

  -- 5. Insert the appointment
  INSERT INTO appointments (
    doctor_id, patient_session_id, scheduled_at, duration_minutes,
    status, service_type, consultation_type, chief_complaint,
    consultation_fee, grace_period_minutes
  ) VALUES (
    p_doctor_id, p_patient_session_id, p_scheduled_at, p_duration_minutes,
    'booked', p_service_type, p_consultation_type, p_chief_complaint,
    p_consultation_fee, p_grace_period_minutes
  )
  RETURNING appointments.appointment_id INTO v_new_id;

  -- Return the new appointment
  RETURN QUERY
    SELECT a.appointment_id, a.doctor_id, a.scheduled_at, a.status::text, a.created_at
    FROM appointments a
    WHERE a.appointment_id = v_new_id;
END;
$$;

-- ============================================================================
-- reschedule_appointment: Atomically reschedule an existing appointment
-- ============================================================================
CREATE OR REPLACE FUNCTION reschedule_appointment(
  p_appointment_id      UUID,
  p_new_scheduled_at    TIMESTAMPTZ,
  p_reschedule_reason   TEXT DEFAULT ''
)
RETURNS TABLE (
  new_appointment_id UUID,
  old_appointment_id UUID,
  doctor_id          UUID,
  scheduled_at       TIMESTAMPTZ,
  status             TEXT,
  created_at         TIMESTAMPTZ
)
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
DECLARE
  v_old appointments%ROWTYPE;
  v_lock_key BIGINT;
  v_day_of_week SMALLINT;
  v_time_of_day TIME;
  v_slot_end TIME;
  v_daily_count INTEGER;
  v_max_per_day SMALLINT;
  v_new_id UUID;
BEGIN
  -- Fetch the old appointment
  SELECT * INTO v_old FROM appointments WHERE appointments.appointment_id = p_appointment_id;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'Appointment not found';
  END IF;

  -- Only booked, confirmed, or missed appointments can be rescheduled
  IF v_old.status NOT IN ('booked', 'confirmed', 'missed') THEN
    RAISE EXCEPTION 'Cannot reschedule an appointment with status %', v_old.status;
  END IF;

  -- Advisory lock keyed on doctor_id
  v_lock_key := ('x' || left(md5(v_old.doctor_id::text), 15))::bit(60)::bigint;
  PERFORM pg_advisory_xact_lock(v_lock_key);

  -- Validate new time is in the future
  IF p_new_scheduled_at <= now() THEN
    RAISE EXCEPTION 'Cannot reschedule to a time in the past';
  END IF;

  -- Validate availability slot
  v_day_of_week := EXTRACT(DOW FROM p_new_scheduled_at AT TIME ZONE 'Africa/Nairobi')::SMALLINT;
  v_time_of_day := (p_new_scheduled_at AT TIME ZONE 'Africa/Nairobi')::TIME;

  SELECT das.end_time INTO v_slot_end
  FROM doctor_availability_slots das
  WHERE das.doctor_id = v_old.doctor_id
    AND das.day_of_week = v_day_of_week
    AND das.is_active = true
    AND das.start_time <= v_time_of_day
    AND das.end_time > v_time_of_day
  LIMIT 1;

  IF v_slot_end IS NULL THEN
    RAISE EXCEPTION 'Doctor is not available at the new time';
  END IF;

  -- Check for time overlap (excluding the old appointment)
  IF EXISTS (
    SELECT 1 FROM appointments a
    WHERE a.doctor_id = v_old.doctor_id
      AND a.appointment_id != p_appointment_id
      AND a.status IN ('booked', 'confirmed', 'in_progress')
      AND tstzrange(a.scheduled_at, a.scheduled_at + (a.duration_minutes || ' minutes')::interval)
          && tstzrange(p_new_scheduled_at, p_new_scheduled_at + (v_old.duration_minutes || ' minutes')::interval)
  ) THEN
    RAISE EXCEPTION 'New time slot conflicts with an existing appointment';
  END IF;

  -- Check daily limit for the new date
  SELECT max_appointments_per_day INTO v_max_per_day
  FROM doctor_profiles
  WHERE doctor_profiles.doctor_id = v_old.doctor_id;

  v_max_per_day := COALESCE(v_max_per_day, 10);

  SELECT COUNT(*) INTO v_daily_count
  FROM appointments a
  WHERE a.doctor_id = v_old.doctor_id
    AND a.appointment_id != p_appointment_id
    AND a.status IN ('booked', 'confirmed', 'in_progress', 'completed')
    AND (a.scheduled_at AT TIME ZONE 'Africa/Nairobi')::date
        = (p_new_scheduled_at AT TIME ZONE 'Africa/Nairobi')::date;

  IF v_daily_count >= v_max_per_day THEN
    RAISE EXCEPTION 'Doctor has reached the maximum appointments for the new date';
  END IF;

  -- Mark old appointment as rescheduled
  UPDATE appointments
  SET status = 'rescheduled'
  WHERE appointments.appointment_id = p_appointment_id;

  -- Insert new appointment linked to old one
  INSERT INTO appointments (
    doctor_id, patient_session_id, scheduled_at, duration_minutes,
    status, service_type, consultation_type, chief_complaint,
    consultation_fee, grace_period_minutes, rescheduled_from
  ) VALUES (
    v_old.doctor_id, v_old.patient_session_id, p_new_scheduled_at, v_old.duration_minutes,
    'booked', v_old.service_type, v_old.consultation_type, v_old.chief_complaint,
    v_old.consultation_fee, v_old.grace_period_minutes, p_appointment_id
  )
  RETURNING appointments.appointment_id INTO v_new_id;

  -- Return the new appointment
  RETURN QUERY
    SELECT a.appointment_id, p_appointment_id, a.doctor_id, a.scheduled_at, a.status::text, a.created_at
    FROM appointments a
    WHERE a.appointment_id = v_new_id;
END;
$$;
