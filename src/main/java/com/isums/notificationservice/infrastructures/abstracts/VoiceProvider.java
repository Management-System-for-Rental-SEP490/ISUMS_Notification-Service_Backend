package com.isums.notificationservice.infrastructures.abstracts;

import com.isums.notificationservice.domains.dtos.SpeedSmsVoiceRequest;
import com.isums.notificationservice.domains.dtos.SpeedSmsVoiceResponse;

/**
 * Voice-call abstraction implemented by Stringee. Kept as an interface
 * so swapping vendors later only touches the impl + the router. DTO
 * names retain the legacy {@code SpeedSms*} prefix from the previous
 * provider — semantics are generic.
 */
public interface VoiceProvider {

    /** Provider id for routing / metrics: "STRINGEE". */
    String providerId();

    /** Dial + speak. Returns provider callId on success. */
    SpeedSmsVoiceResponse sendVoiceCall(SpeedSmsVoiceRequest request);

    /** Verify webhook payload (HMAC / JWT depending on provider). */
    boolean verifyWebhookSignature(String rawBody, String signature);
}
