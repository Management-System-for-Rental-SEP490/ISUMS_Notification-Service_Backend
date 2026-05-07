-- PDPL (Nghị định 13/2023/NĐ-CP) + Thông tư 22/2021/TT-BTTTT compliance:
-- voice consent must be auditable with version of T&C text, IP address
-- the user submitted from, and user agent. An immutable history table
-- preserves every grant/revoke for the 5-year retention window required
-- by Vietnamese telecom regulation.

ALTER TABLE user_notification_preferences
    ADD COLUMN IF NOT EXISTS voice_consent_text_version VARCHAR(20),
    ADD COLUMN IF NOT EXISTS voice_consent_ip           INET,
    ADD COLUMN IF NOT EXISTS voice_consent_user_agent   TEXT;

-- Append-only audit log. Triggers fire on every grant or revoke; rows
-- never deleted (telecom audit may demand 5-year proof).
CREATE TABLE IF NOT EXISTS voice_consent_history (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL,
    action          VARCHAR(20) NOT NULL,              -- GRANTED | REVOKED | EXPIRED
    text_version    VARCHAR(20),                        -- e.g. "v1.0-2026-04"
    ip              INET,
    user_agent      TEXT,
    initiated_by    VARCHAR(20) NOT NULL DEFAULT 'USER',-- USER | ADMIN | SYSTEM
    initiated_by_id UUID,                               -- if ADMIN, who
    note            TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS ix_voice_consent_user
    ON voice_consent_history(user_id, created_at DESC);
