package com.isums.notificationservice.domains.dtos;

import com.isums.notificationservice.domains.enums.LocaleType;
import com.isums.notificationservice.domains.enums.VoiceCallStatus;

import java.time.Instant;
import java.util.UUID;

public record VoiceCallDto(
        UUID id,
        UUID userId,
        String alertId,
        String eventType,
        LocaleType locale,
        VoiceCallStatus status,
        String dtmfReceived,
        Instant acknowledgedAt,
        int attemptNumber,
        int maxAttempts,
        Integer durationSec,
        Integer costVnd,
        Instant createdAt
) {}
