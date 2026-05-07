-- Voice / multi-channel notification infrastructure.
--
-- Adds to the existing email-centric tables (email_templates / email_template_versions /
-- manager_notifications) a second family for:
--   - Per-user channel preferences (opt-in + quiet hours + retry / rate-limit knobs)
--   - Subscription tier gate (FREE vs PREMIUM 19k/month)
--   - Multi-channel template versions (VOICE / SMS / PUSH / ZNS) in vi_VN / en_US / ja_JP
--   - Voice-call job audit + escalation chain
--   - Pre-synthesised TTS audio cache (for Japanese, since SpeedSMS TTS is vi-only)
--
-- RDS = prod data (project_isums_infra memory), so every schema change is
-- expressed as a Flyway migration rather than ddl-auto=update.

-- ============================================================
-- user_notification_preferences
-- ============================================================
CREATE TABLE IF NOT EXISTS user_notification_preferences (
    user_id UUID PRIMARY KEY,

    language VARCHAR(20) NOT NULL DEFAULT 'vi_VN'
        CHECK (language IN ('vi_VN', 'en_US', 'ja_JP')),

    -- Channel opt-in
    email_enabled    BOOLEAN NOT NULL DEFAULT TRUE,
    push_enabled     BOOLEAN NOT NULL DEFAULT TRUE,
    sms_enabled      BOOLEAN NOT NULL DEFAULT FALSE,
    voice_enabled    BOOLEAN NOT NULL DEFAULT FALSE,

    -- Quiet hours — stored as VN-local time (Asia/Ho_Chi_Minh). CRITICAL
    -- alerts (gas, fire, power-lost) bypass quiet hours when the override
    -- flag is TRUE.
    quiet_hours_start TIME NOT NULL DEFAULT '22:00',
    quiet_hours_end   TIME NOT NULL DEFAULT '06:00',
    quiet_hours_override_critical BOOLEAN NOT NULL DEFAULT TRUE,

    -- Voice knobs (user-configurable; service enforces tier-based caps)
    voice_max_retries        INT  NOT NULL DEFAULT 2 CHECK (voice_max_retries BETWEEN 0 AND 5),
    voice_retry_interval_sec INT  NOT NULL DEFAULT 120 CHECK (voice_retry_interval_sec BETWEEN 30 AND 600),
    voice_rate_limit_sec     INT  NOT NULL DEFAULT 300 CHECK (voice_rate_limit_sec BETWEEN 60 AND 3600),
    voice_gender             VARCHAR(10) NOT NULL DEFAULT 'FEMALE'
        CHECK (voice_gender IN ('MALE', 'FEMALE')),
    voice_speed              NUMERIC(3,2) NOT NULL DEFAULT 1.00
        CHECK (voice_speed BETWEEN 0.80 AND 1.20),
    dtmf_ack_enabled         BOOLEAN NOT NULL DEFAULT TRUE,

    -- Escalation: when voice retry exhausts, dispatch to another user
    -- (usually tenant → landlord). NULL = resolve via HouseGrpc landlord lookup.
    escalation_enabled       BOOLEAN NOT NULL DEFAULT TRUE,
    escalation_target_user_id UUID,

    -- Opt-in timestamp for regulatory proof (voice calls need explicit consent)
    voice_consent_given_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_prefs_escalation_target
    ON user_notification_preferences(escalation_target_user_id);


-- ============================================================
-- notification_subscriptions
-- ============================================================
CREATE TABLE IF NOT EXISTS notification_subscriptions (
    user_id UUID PRIMARY KEY,

    tier VARCHAR(20) NOT NULL DEFAULT 'FREE'
        CHECK (tier IN ('FREE', 'PREMIUM')),

    premium_started_at TIMESTAMPTZ,
    premium_until      TIMESTAMPTZ,

    -- Caps reset monthly; sourced from tier defaults but per-user override allowed
    voice_quota_monthly INT NOT NULL DEFAULT 0,
    voice_used_this_month INT NOT NULL DEFAULT 0,

    sms_quota_monthly INT NOT NULL DEFAULT 0,
    sms_used_this_month INT NOT NULL DEFAULT 0,

    quota_reset_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_subs_premium_until
    ON notification_subscriptions(premium_until)
    WHERE tier = 'PREMIUM';


-- ============================================================
-- channel_templates (non-email channels — VOICE/SMS/PUSH/ZNS)
-- email_templates table is kept intact for email-specific bookkeeping.
-- ============================================================
CREATE TABLE IF NOT EXISTS channel_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_key VARCHAR(100) NOT NULL,
    channel      VARCHAR(20)  NOT NULL
        CHECK (channel IN ('VOICE', 'SMS', 'PUSH', 'ZNS')),
    event_type   VARCHAR(80),
    category     VARCHAR(50),
    recipient_type VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    CONSTRAINT uq_channel_tpl_key_channel UNIQUE (template_key, channel)
);

CREATE INDEX IF NOT EXISTS ix_ch_tpl_event_type
    ON channel_templates(event_type, channel);


-- ============================================================
-- channel_template_versions
-- ============================================================
CREATE TABLE IF NOT EXISTS channel_template_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id UUID NOT NULL REFERENCES channel_templates(id) ON DELETE CASCADE,

    locale VARCHAR(20) NOT NULL
        CHECK (locale IN ('vi_VN', 'en_US', 'ja_JP')),

    version INT NOT NULL,
    status  VARCHAR(20) NOT NULL DEFAULT 'DRAFT'
        CHECK (status IN ('DRAFT', 'APPROVED', 'ACTIVE', 'DEPRECATED')),

    -- body: rendered via Mustache, interpolated with alert vars
    body TEXT NOT NULL,

    -- SSML alt for TTS channels — better pronunciation of numbers/units.
    -- NULL for SMS/PUSH/ZNS.
    ssml TEXT,

    -- Short title for PUSH/SMS channels (ignored for VOICE)
    title VARCHAR(200),

    allowed_vars JSONB,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),

    CONSTRAINT uq_ch_tpl_ver UNIQUE (template_id, locale, version)
);

CREATE INDEX IF NOT EXISTS ix_ch_tplver_template_locale_status
    ON channel_template_versions(template_id, locale, status);


-- ============================================================
-- voice_call_jobs
-- ============================================================
CREATE TABLE IF NOT EXISTS voice_call_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id    UUID NOT NULL,
    alert_id   VARCHAR(100),          -- e.g. DynamoDB esp32_alerts.alertId
    event_type VARCHAR(80) NOT NULL,

    phone    VARCHAR(40) NOT NULL,    -- E.164 formatted
    locale   VARCHAR(20) NOT NULL,

    template_id        UUID,
    template_version_id UUID,

    rendered_text TEXT NOT NULL,      -- audit of what we asked provider to speak

    provider         VARCHAR(20) NOT NULL DEFAULT 'SPEEDSMS',
    provider_call_id VARCHAR(100),

    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'DIALING', 'ANSWERED', 'NO_ANSWER',
                          'BUSY', 'FAILED', 'ACKNOWLEDGED', 'ESCALATED', 'SKIPPED')),

    dtmf_received VARCHAR(10),
    acknowledged_at TIMESTAMPTZ,

    attempt_number INT NOT NULL DEFAULT 1,
    max_attempts   INT NOT NULL DEFAULT 3,
    next_retry_at  TIMESTAMPTZ,

    duration_sec INT,
    cost_vnd     INT,
    recording_url TEXT,

    error_message TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_voice_job_user_created   ON voice_call_jobs(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_voice_job_status_retry   ON voice_call_jobs(status, next_retry_at);
CREATE INDEX IF NOT EXISTS ix_voice_job_provider_call  ON voice_call_jobs(provider_call_id);
CREATE INDEX IF NOT EXISTS ix_voice_job_alert          ON voice_call_jobs(alert_id);


-- ============================================================
-- voice_call_escalations
-- ============================================================
CREATE TABLE IF NOT EXISTS voice_call_escalations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    original_call_id    UUID NOT NULL REFERENCES voice_call_jobs(id) ON DELETE CASCADE,
    escalated_call_id   UUID REFERENCES voice_call_jobs(id) ON DELETE SET NULL,
    escalated_to_user_id UUID NOT NULL,
    reason VARCHAR(40) NOT NULL
        CHECK (reason IN ('NO_ANSWER_MAX_RETRIES', 'DTMF_KEY_2', 'MANUAL')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS ix_esc_original ON voice_call_escalations(original_call_id);


-- ============================================================
-- voice_audio_cache — pre-synthesised TTS (mainly for ja_JP via AWS Polly)
-- SpeedSMS native TTS only supports vi; for ja/en we pre-render to S3 and
-- pass the audio URL to SpeedSMS "play_url" mode.
-- ============================================================
CREATE TABLE IF NOT EXISTS voice_audio_cache (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Cache key = hash of (template_version_id, locale, rendered_text, voice_gender, voice_speed)
    cache_key VARCHAR(128) NOT NULL UNIQUE,

    locale       VARCHAR(20) NOT NULL,
    voice_gender VARCHAR(10) NOT NULL,
    voice_speed  NUMERIC(3,2) NOT NULL,

    rendered_text TEXT NOT NULL,
    s3_bucket VARCHAR(200) NOT NULL,
    s3_key    VARCHAR(400) NOT NULL,
    public_url TEXT NOT NULL,

    duration_sec INT,
    bytes        INT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    hit_count INT NOT NULL DEFAULT 0
);


-- ============================================================
-- Seed default preferences insert helper (for ApplicationRunner)
-- No rows here — seeder creates per-user lazily on first dispatch.
-- ============================================================
