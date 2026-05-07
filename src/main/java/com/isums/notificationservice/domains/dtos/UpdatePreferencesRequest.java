package com.isums.notificationservice.domains.dtos;

import com.isums.notificationservice.domains.enums.LocaleType;
import com.isums.notificationservice.domains.enums.VoiceGender;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Every field optional — null = keep existing. Ranges validated here so
 * a malformed app request never writes junk into preferences.
 */
public record UpdatePreferencesRequest(
        LocaleType language,
        Boolean emailEnabled,
        Boolean pushEnabled,
        Boolean smsEnabled,
        Boolean voiceEnabled,
        Boolean quietHoursEnabled,
        LocalTime quietHoursStart,
        LocalTime quietHoursEnd,
        Boolean quietHoursOverrideCritical,

        @Min(0) @Max(5)
        Integer voiceMaxRetries,

        @Min(30) @Max(600)
        Integer voiceRetryIntervalSec,

        @Min(60) @Max(3600)
        Integer voiceRateLimitSec,

        VoiceGender voiceGender,

        @DecimalMin("0.80") @DecimalMax("1.20")
        BigDecimal voiceSpeed,

        Boolean dtmfAckEnabled,
        Boolean escalationEnabled,
        UUID escalationTargetUserId,

        // Explicit boolean — true to grant consent now, false to revoke,
        // null to leave unchanged. Revoking also turns voice_enabled off.
        Boolean voiceConsentGranted
) {}
