-- ============================================================
-- Automatic audit logging for all important database events.
-- Inserts into admin_logs whenever key tables change.
-- admin_id is set to the user making the change (via auth.uid())
-- or a system UUID for anonymous / service-role operations.
-- ============================================================

-- System UUID for automated/service-role events
-- 00000000-0000-0000-0000-000000000000

-- ── Helper function ──────────────────────────────────────────
create or replace function audit_log_event(
  p_action text,
  p_target_type text,
  p_target_id text,
  p_details jsonb default null
) returns void as $$
begin
  insert into admin_logs (admin_id, action, target_type, target_id, details)
  values (
    coalesce(auth.uid(), '00000000-0000-0000-0000-000000000000'::uuid),
    p_action,
    p_target_type,
    p_target_id,
    p_details
  );
end;
$$ language plpgsql security definer;


-- ── 1. Doctor Registration ───────────────────────────────────
create or replace function trg_doctor_registered()
returns trigger as $$
begin
  perform audit_log_event(
    'doctor_registered',
    'doctor_profile',
    NEW.doctor_id::text,
    jsonb_build_object('full_name', NEW.full_name, 'email', NEW.email, 'specialty', NEW.specialty)
  );
  return NEW;
end;
$$ language plpgsql security definer;

drop trigger if exists audit_doctor_registered on doctor_profiles;
create trigger audit_doctor_registered
  after insert on doctor_profiles
  for each row execute function trg_doctor_registered();


-- ── 2. Doctor Verification Status Change ─────────────────────
create or replace function trg_doctor_verification_changed()
returns trigger as $$
begin
  if OLD.is_verified is distinct from NEW.is_verified then
    if NEW.is_verified then
      perform audit_log_event(
        'doctor_verified',
        'doctor_profile',
        NEW.doctor_id::text,
        jsonb_build_object('full_name', NEW.full_name)
      );
    end if;
  end if;

  if OLD.is_available is distinct from NEW.is_available then
    perform audit_log_event(
      case when NEW.is_available then 'doctor_activated' else 'doctor_deactivated' end,
      'doctor_profile',
      NEW.doctor_id::text,
      jsonb_build_object('full_name', NEW.full_name)
    );
  end if;

  if NEW.rejection_reason is not null and OLD.rejection_reason is distinct from NEW.rejection_reason then
    perform audit_log_event(
      'doctor_rejected',
      'doctor_profile',
      NEW.doctor_id::text,
      jsonb_build_object('full_name', NEW.full_name, 'reason', NEW.rejection_reason)
    );
  end if;

  return NEW;
end;
$$ language plpgsql security definer;

drop trigger if exists audit_doctor_verification on doctor_profiles;
create trigger audit_doctor_verification
  after update on doctor_profiles
  for each row execute function trg_doctor_verification_changed();


-- ── 3. Consultation Created / Status Changed ─────────────────
create or replace function trg_consultation_event()
returns trigger as $$
begin
  if TG_OP = 'INSERT' then
    perform audit_log_event(
      'consultation_created',
      'consultation',
      NEW.consultation_id::text,
      jsonb_build_object('service_type', NEW.service_type, 'status', NEW.status, 'doctor_id', NEW.doctor_id)
    );
  elsif TG_OP = 'UPDATE' and OLD.status is distinct from NEW.status then
    perform audit_log_event(
      'consultation_' || NEW.status,
      'consultation',
      NEW.consultation_id::text,
      jsonb_build_object('old_status', OLD.status, 'new_status', NEW.status, 'doctor_id', NEW.doctor_id)
    );
  end if;
  return NEW;
end;
$$ language plpgsql security definer;

drop trigger if exists audit_consultation on consultations;
create trigger audit_consultation
  after insert or update on consultations
  for each row execute function trg_consultation_event();


-- ── 4. Payment Completed ─────────────────────────────────────
create or replace function trg_payment_event()
returns trigger as $$
begin
  if TG_OP = 'INSERT' then
    perform audit_log_event(
      'payment_created',
      'payment',
      NEW.payment_id::text,
      jsonb_build_object('amount', NEW.amount, 'currency', NEW.currency, 'status', NEW.status)
    );
  elsif TG_OP = 'UPDATE' and OLD.status is distinct from NEW.status and NEW.status = 'completed' then
    perform audit_log_event(
      'payment_completed',
      'payment',
      NEW.payment_id::text,
      jsonb_build_object('amount', NEW.amount, 'currency', NEW.currency)
    );
  end if;
  return NEW;
end;
$$ language plpgsql security definer;

drop trigger if exists audit_payment on payments;
create trigger audit_payment
  after insert or update on payments
  for each row execute function trg_payment_event();


-- ── 5. Doctor Rating Submitted ───────────────────────────────
create or replace function trg_rating_submitted()
returns trigger as $$
begin
  perform audit_log_event(
    'rating_submitted',
    'doctor_rating',
    NEW.rating_id::text,
    jsonb_build_object('doctor_id', NEW.doctor_id, 'rating', NEW.rating, 'has_comment', NEW.comment is not null)
  );
  return NEW;
end;
$$ language plpgsql security definer;

drop trigger if exists audit_rating_submitted on doctor_ratings;
create trigger audit_rating_submitted
  after insert on doctor_ratings
  for each row execute function trg_rating_submitted();


-- ── 6. Patient Session Created ───────────────────────────────
create or replace function trg_patient_session_created()
returns trigger as $$
begin
  perform audit_log_event(
    'patient_session_created',
    'patient_session',
    NEW.session_id::text,
    jsonb_build_object('region', NEW.region)
  );
  return NEW;
end;
$$ language plpgsql security definer;

drop trigger if exists audit_patient_session on patient_sessions;
create trigger audit_patient_session
  after insert on patient_sessions
  for each row execute function trg_patient_session_created();


-- ── 7. User Role Changes ─────────────────────────────────────
create or replace function trg_user_role_event()
returns trigger as $$
begin
  if TG_OP = 'INSERT' then
    perform audit_log_event(
      'role_assigned',
      'user_role',
      NEW.user_id::text,
      jsonb_build_object('role', NEW.role_name)
    );
  elsif TG_OP = 'DELETE' then
    perform audit_log_event(
      'role_revoked',
      'user_role',
      OLD.user_id::text,
      jsonb_build_object('role', OLD.role_name)
    );
  end if;
  return coalesce(NEW, OLD);
end;
$$ language plpgsql security definer;

drop trigger if exists audit_user_role on user_roles;
create trigger audit_user_role
  after insert or delete on user_roles
  for each row execute function trg_user_role_event();
