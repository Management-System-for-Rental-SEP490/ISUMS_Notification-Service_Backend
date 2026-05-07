package com.isums.notificationservice.domains.dtos;

/**
 * Webhook body SpeedSMS posts after a voice call completes. The exact
 * field names may differ slightly between SpeedSMS plans; the controller
 * tolerates extras via Jackson {@code @JsonIgnoreProperties}.
 */
public record SpeedSmsWebhookPayload(
        String callId,
        String status,      // ANSWERED | NO_ANSWER | BUSY | FAILED
        Integer duration,   // seconds
        Integer cost,       // VND
        String dtmf,        // digit user pressed
        String recordingUrl,
        String errorMessage,
        Long occurredAt
) {}
