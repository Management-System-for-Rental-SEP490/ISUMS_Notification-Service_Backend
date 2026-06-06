-- NotificationCategory is validated by the Java enum. A static PostgreSQL
-- CHECK constraint gets stale whenever the application adds a category and
-- causes otherwise valid Kafka events to fail permanently.
ALTER TABLE manager_notifications
    DROP CONSTRAINT IF EXISTS manager_notifications_category_check;
