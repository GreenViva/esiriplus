-- Add VIDEO_CALL_INCOMING to notification_type_enum so push notifications
-- for incoming calls can be stored in the notifications table.
ALTER TYPE notification_type_enum ADD VALUE IF NOT EXISTS 'VIDEO_CALL_INCOMING';
