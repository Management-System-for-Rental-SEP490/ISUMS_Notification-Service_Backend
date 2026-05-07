package com.isums.notificationservice.services;

import com.isums.notificationservice.domains.enums.SubscriptionTier;

public final class TierQuotaPolicy {

    private TierQuotaPolicy() {}

    public static int voiceQuotaFor(SubscriptionTier tier) {
        return switch (tier) {
            case PREMIUM -> 20;
            case FREE    -> 0;
        };
    }

    public static int smsQuotaFor(SubscriptionTier tier) {
        return switch (tier) {
            case PREMIUM -> 30;
            case FREE    -> 0;
        };
    }

    public static int minRetryIntervalSec(SubscriptionTier tier) {
        return switch (tier) {
            case PREMIUM -> 30;
            case FREE    -> 120;
        };
    }

    public static int maxVoiceRetries(SubscriptionTier tier) {
        return switch (tier) {
            case PREMIUM -> 3;
            case FREE    -> 0;
        };
    }
}

