package com.isums.notificationservice.infrastructures.abstracts;

import com.isums.notificationservice.domains.dtos.SpeedSmsVoiceResponse;

/**
 * SMS-only provider. Split from {@link VoiceProvider} so voice keeps
 * going through Stringee while SMS goes through AWS SNS.
 */
public interface SmsProvider {

    /** Provider id for routing / metrics: "AWS_SNS". */
    String providerId();

    /** Send a transactional SMS. */
    SpeedSmsVoiceResponse sendSms(String phone, String text);
}
