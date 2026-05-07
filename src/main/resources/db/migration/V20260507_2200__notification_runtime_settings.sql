CREATE TABLE IF NOT EXISTS notification_runtime_settings (
    setting_key   VARCHAR(80)  PRIMARY KEY,
    setting_value VARCHAR(500) NOT NULL,
    updated_by    UUID,
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

INSERT INTO notification_runtime_settings (setting_key, setting_value)
VALUES ('voice.provider', 'STRINGEE')
ON CONFLICT (setting_key) DO NOTHING;
