-- Phase 3 i18n: store per-locale translations for manager notification title/body.
-- Source columns (title, body) keep authoring locale (default vi); translations
-- map populated asynchronously by AI-Service.

ALTER TABLE manager_notifications
    ADD COLUMN IF NOT EXISTS title_translations TEXT,
    ADD COLUMN IF NOT EXISTS body_translations  TEXT;

COMMENT ON COLUMN manager_notifications.title_translations IS
    'JSON map of locale -> translated title. Reserved keys: _source, _auto.';
COMMENT ON COLUMN manager_notifications.body_translations IS
    'JSON map of locale -> translated body. Reserved keys: _source, _auto.';
