-- Master on/off for the quiet-hours window. Existing rows default to
-- true so behaviour matches what the user already expected: window
-- enforced unless they explicitly turn it off.
ALTER TABLE user_notification_preferences
    ADD COLUMN IF NOT EXISTS quiet_hours_enabled BOOLEAN NOT NULL DEFAULT TRUE;
