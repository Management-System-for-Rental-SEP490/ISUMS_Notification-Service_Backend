package com.isums.notificationservice.domains.dtos;

import java.time.Instant;
import java.util.UUID;

public record SubscriptionPlanDto(
        UUID id,
        String code,
        String nameTranslations,    // raw i18n JSON — FE picks based on locale
        Integer durationDays,
        Integer priceVnd,
        Integer voiceQuotaMonthly,
        Integer smsQuotaMonthly,
        Integer sortOrder,
        Boolean isActive,
        Boolean isFeatured,
        Instant createdAt,
        Instant updatedAt
) {}
