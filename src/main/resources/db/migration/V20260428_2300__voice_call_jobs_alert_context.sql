-- Denormalize alert payload onto voice_call_jobs so the webhook handler
-- can re-dispatch the same alert to landlord / manager when the tenant
-- presses DTMF=2 or doesn't answer after retries.
--
-- Without this we'd have to query DynamoDB esp32_alerts at escalation
-- time, which adds an external dependency to the webhook hot path.
ALTER TABLE voice_call_jobs
    ADD COLUMN IF NOT EXISTS house_id    VARCHAR(64),
    ADD COLUMN IF NOT EXISTS area_id     VARCHAR(64),
    ADD COLUMN IF NOT EXISTS area_name   VARCHAR(200),
    ADD COLUMN IF NOT EXISTS thing       VARCHAR(100),
    ADD COLUMN IF NOT EXISTS metric      VARCHAR(40),
    ADD COLUMN IF NOT EXISTS alert_value NUMERIC(10, 2),
    ADD COLUMN IF NOT EXISTS alert_unit  VARCHAR(20);
