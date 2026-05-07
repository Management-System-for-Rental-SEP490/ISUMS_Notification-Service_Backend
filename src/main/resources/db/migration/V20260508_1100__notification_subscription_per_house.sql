ALTER TABLE notification_subscriptions
    ADD COLUMN IF NOT EXISTS house_id uuid;

DELETE FROM notification_subscriptions WHERE tier = 'FREE';

DELETE FROM notification_subscriptions WHERE house_id IS NULL;

ALTER TABLE notification_subscriptions
    ALTER COLUMN house_id SET NOT NULL;

ALTER TABLE notification_subscriptions
    DROP CONSTRAINT IF EXISTS notification_subscriptions_pkey;

ALTER TABLE notification_subscriptions
    ADD PRIMARY KEY (user_id, house_id);

CREATE INDEX IF NOT EXISTS idx_notification_subscriptions_user_id
    ON notification_subscriptions(user_id);
