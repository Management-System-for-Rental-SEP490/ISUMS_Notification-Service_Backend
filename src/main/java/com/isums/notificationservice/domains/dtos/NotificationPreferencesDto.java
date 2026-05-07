package com.isums.notificationservice.domains.dtos;

import com.isums.notificationservice.domains.enums.LocaleType;
import com.isums.notificationservice.domains.enums.VoiceGender;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

public record NotificationPreferencesDto(
        UUID userId,
        LocaleType language,
        boolean emailEnabled,
        boolean pushEnabled,
        boolean smsEnabled,
        boolean voiceEnabled,
        boolean quietHoursEnabled,
        LocalTime quietHoursStart,
        LocalTime quietHoursEnd,
        boolean quietHoursOverrideCritical,
        int voiceMaxRetries,
        int voiceRetryIntervalSec,
        int voiceRateLimitSec,
        VoiceGender voiceGender,
        BigDecimal voiceSpeed,
        boolean dtmfAckEnabled,
        boolean escalationEnabled,
        UUID escalationTargetUserId,
        Instant voiceConsentGivenAt
) {}
