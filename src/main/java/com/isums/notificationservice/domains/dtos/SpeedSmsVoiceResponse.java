package com.isums.notificationservice.domains.dtos;

public record SpeedSmsVoiceResponse(
        boolean ok,
        String callId,
        String status,
        String errorMessage
) {}
