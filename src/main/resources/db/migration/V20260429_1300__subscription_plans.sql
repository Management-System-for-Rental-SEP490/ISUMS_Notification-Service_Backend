-- Landlord-managed subscription catalogue. Replaces the hard-coded
-- 1/3/6/12 month tiers in the FE so the business owner can adjust
-- pricing or roll new promotions without a code deploy.
--
-- Duration is measured in DAYS so we can offer everything from a 7-day
-- trial (post-onboarding bait) to a multi-year discount tier without
-- forcing month-aligned arithmetic on the activation logic.

CREATE TABLE IF NOT EXISTS subscription_plans (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code                 VARCHAR(40) UNIQUE NOT NULL,
    -- e.g. PREMIUM_1M, PREMIUM_TRIAL_7D, PREMIUM_ANNUAL
    name_translations    TEXT,
    -- ISUMS i18n JSON blob: {"vi":"3 tháng","en":"3 months","ja":"3ヶ月"}
    duration_days        INT NOT NULL,
    price_vnd            INT NOT NULL,
    voice_quota_monthly  INT NOT NULL DEFAULT 100,
    sms_quota_monthly    INT NOT NULL DEFAULT 200,
    sort_order           INT NOT NULL DEFAULT 0,
    is_active            BOOLEAN NOT NULL DEFAULT TRUE,
    is_featured          BOOLEAN NOT NULL DEFAULT FALSE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by           UUID,
    updated_by           UUID,
    CONSTRAINT chk_duration_positive CHECK (duration_days > 0),
    CONSTRAINT chk_price_nonneg     CHECK (price_vnd >= 0)
);

CREATE INDEX IF NOT EXISTS ix_plan_active_sort
    ON subscription_plans(is_active, sort_order);

-- Seed canonical defaults — landlord can edit / disable / supplement.
INSERT INTO subscription_plans
    (code, name_translations, duration_days, price_vnd,
     voice_quota_monthly, sms_quota_monthly, sort_order, is_featured)
VALUES
    -- 12.000đ là ngưỡng an toàn — VNPay khuyến nghị >=10.000đ và một số
    -- ngân hàng (Sacombank, ACB...) reject thẻ dưới 10k. Để 12k cho buffer
    -- mà vẫn đủ rẻ để cảm giác như "trial".
    ('PREMIUM_TRIAL_7D',  '{"vi":"Dùng thử 7 ngày","en":"7-day trial","ja":"7日間のトライアル"}',
     7,   12000,  30,  60,   1, false),
    ('PREMIUM_1M',        '{"vi":"1 tháng","en":"1 month","ja":"1ヶ月"}',
     30,  19000,  100, 200,  10, false),
    ('PREMIUM_3M',        '{"vi":"3 tháng","en":"3 months","ja":"3ヶ月"}',
     90,  54000,  100, 200,  20, true),
    ('PREMIUM_6M',        '{"vi":"6 tháng","en":"6 months","ja":"6ヶ月"}',
     180, 102000, 100, 200,  30, false),
    ('PREMIUM_12M',       '{"vi":"12 tháng","en":"12 months","ja":"12ヶ月"}',
     365, 190000, 100, 200,  40, false)
ON CONFLICT (code) DO NOTHING;
