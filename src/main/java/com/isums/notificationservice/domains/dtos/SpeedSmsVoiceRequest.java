package com.isums.notificationservice.domains.dtos;

import java.util.UUID;

/**
 * Generic voice-call request handed to a {@code VoiceProvider}. Named
 * "SpeedSms*" for back-compat with the original SpeedSMS-only design;
 * field semantics are provider-agnostic.
 *
 * <p>For SpeedSMS Voice OTP only digits in {@code tts} are read.<br>
 * For Stringee, the provider fetches {@code answerUrlBase + ?jobId=...}
 * to retrieve the SCCO at call-connect time, so {@code jobId} is the
 * canonical join key.
 */
public record SpeedSmsVoiceRequest(
        String phone,
        String tts,
        String audioUrl,
        int loop,
        String callbackUrl,
        String callerIdName,
        UUID jobId,
        String voiceName,
        boolean interactive
) {
    /** Back-compat constructor — assumes interactive=true (tenant flow). */
    public SpeedSmsVoiceRequest(String phone, String tts, String audioUrl,
                                  int loop, String callbackUrl, String callerIdName) {
        this(phone, tts, audioUrl, loop, callbackUrl, callerIdName, null, null, true);
    }

    /** Constructor without explicit voice — provider picks default. */
    public SpeedSmsVoiceRequest(String phone, String tts, String audioUrl,
                                  int loop, String callbackUrl, String callerIdName, UUID jobId) {
        this(phone, tts, audioUrl, loop, callbackUrl, callerIdName, jobId, null, true);
    }

    /** Constructor without explicit interactive flag — defaults to true. */
    public SpeedSmsVoiceRequest(String phone, String tts, String audioUrl,
                                  int loop, String callbackUrl, String callerIdName,
                                  UUID jobId, String voiceName) {
        this(phone, tts, audioUrl, loop, callbackUrl, callerIdName, jobId, voiceName, true);
    }
}
