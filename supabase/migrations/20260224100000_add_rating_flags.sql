-- Add flagging support to doctor_ratings for HR review
alter table doctor_ratings add column if not exists is_flagged boolean default false;
alter table doctor_ratings add column if not exists flagged_by uuid references auth.users(id);
alter table doctor_ratings add column if not exists flagged_at timestamptz;
