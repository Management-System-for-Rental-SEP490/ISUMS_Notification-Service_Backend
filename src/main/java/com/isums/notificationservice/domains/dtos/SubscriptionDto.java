package com.isums.notificationservice.domains.dtos;

import com.isums.notificationservice.domains.enums.SubscriptionTier;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionDto(
        UUID userId,
        SubscriptionTier tier,
        Instant premiumStartedAt,
        Instant premiumUntil,
        int voiceQuotaMonthly,
        int voiceUsedThisMonth,
        int voiceRemaining,
        int smsQuotaMonthly,
        int smsUsedThisMonth,
        int smsRemaining,
        Instant quotaResetAt
) {}
