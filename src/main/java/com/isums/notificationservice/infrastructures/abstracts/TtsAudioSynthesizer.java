package com.isums.notificationservice.infrastructures.abstracts;

import com.isums.notificationservice.domains.enums.LocaleType;
import com.isums.notificationservice.domains.enums.VoiceGender;

import java.math.BigDecimal;

public interface TtsAudioSynthesizer {

    /**
     * Returns a publicly-reachable audio URL (MP3) speaking the given text
     * in the given locale/gender/speed. Cached by (rendered_text, locale,
     * gender, speed) hash — duplicate texts reuse the same S3 object.
     *
     * <p>Used for {@code ja_JP} (and optionally {@code en_US}) where the
     * VN SpeedSMS TTS engine either doesn't support the language or
     * pronounces it poorly. For {@code vi_VN}, callers should prefer
     * SpeedSMS native TTS (cheaper + lower latency) and skip this path.
     */
    String synthesizeAndCache(
            String text,
            LocaleType locale,
            VoiceGender gender,
            BigDecimal speed);
}
