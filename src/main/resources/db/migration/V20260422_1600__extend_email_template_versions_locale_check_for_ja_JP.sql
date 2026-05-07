-- Widen email_template_versions.locale CHECK to accept ja_JP alongside
-- the previous vi_VN / en_US values.
--
-- Background: the column is @Enumerated(STRING) on EmailTemplateVersion,
-- and Hibernate 6+ DDL autogenerates a CHECK constraint from the enum's
-- values when the table was first created. Adding a new enum constant
-- (LocaleType.ja_JP) does NOT retroactively ALTER that constraint, so
-- the next insert of a ja_JP template row is rejected by Postgres with
-- "violates check constraint email_template_versions_locale_check".
--
-- We replace the constraint rather than drop-and-recreate via JPA because
-- (1) RDS is treated as production (manual migrations only), and
-- (2) a Flyway migration is versioned + replayable across environments,
-- whereas relying on ddl-auto would diverge prod from dev.
--
-- Matches: com.isums.notificationservice.domains.enums.LocaleType

ALTER TABLE email_template_versions
    DROP CONSTRAINT IF EXISTS email_template_versions_locale_check;

ALTER TABLE email_template_versions
    ADD CONSTRAINT email_template_versions_locale_check
    CHECK (locale IN (
        'vi_VN',
        'en_US',
        'ja_JP'
    ));
