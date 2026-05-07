package com.isums.notificationservice.services;

import com.isums.notificationservice.domains.entities.VoiceAudioCache;
import com.isums.notificationservice.domains.enums.LocaleType;
import com.isums.notificationservice.domains.enums.VoiceGender;
import com.isums.notificationservice.infrastructures.abstracts.TtsAudioSynthesizer;
import com.isums.notificationservice.infrastructures.repositories.VoiceAudioCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.polly.PollyClient;
import software.amazon.awssdk.services.polly.model.Engine;
import software.amazon.awssdk.services.polly.model.LanguageCode;
import software.amazon.awssdk.services.polly.model.OutputFormat;
import software.amazon.awssdk.services.polly.model.SynthesizeSpeechRequest;
import software.amazon.awssdk.services.polly.model.TextType;
import software.amazon.awssdk.services.polly.model.VoiceId;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
@Slf4j
public class PollyTtsSynthesizer implements TtsAudioSynthesizer {

    private final PollyClient pollyClient;
    private final S3Client s3Client;
    private final VoiceAudioCacheRepository audioCacheRepo;

    @Value("${app.notification.voice.audio-bucket:}")
    private String audioBucket;

    @Value("${app.notification.voice.audio-public-base:https://%s.s3.ap-southeast-1.amazonaws.com}")
    private String publicUrlBase;

    @Override
    @Transactional
    public String synthesizeAndCache(String text, LocaleType locale,
                                      VoiceGender gender, BigDecimal speed) {
        String cacheKey = hash(text, locale, gender, speed);

        var existing = audioCacheRepo.findByCacheKey(cacheKey);
        if (existing.isPresent()) {
            VoiceAudioCache hit = existing.get();
            hit.setLastUsedAt(Instant.now());
            hit.setHitCount(hit.getHitCount() + 1);
            audioCacheRepo.save(hit);
            return hit.getPublicUrl();
        }

        if (audioBucket == null || audioBucket.isBlank()) {
            throw new IllegalStateException(
                    "app.notification.voice.audio-bucket not configured — "
                    + "cannot synthesize non-VN TTS");
        }

        VoiceId voice = pickVoice(locale, gender);
        String ssml = wrapWithProsody(text, speed);

        SynthesizeSpeechRequest req = SynthesizeSpeechRequest.builder()
                .text(ssml)
                .textType(TextType.SSML)
                .voiceId(voice)
                .outputFormat(OutputFormat.MP3)
                .engine(Engine.NEURAL)
                .languageCode(toLanguageCode(locale))
                .build();

        byte[] audio;
        try (var audioStream = pollyClient.synthesizeSpeech(req)) {
            audio = audioStream.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException("Polly synth failed: " + e.getMessage(), e);
        }

        String s3Key = "voice-tts/" + locale.name() + "/" + cacheKey + ".mp3";
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(audioBucket)
                        .key(s3Key)
                        .contentType("audio/mpeg")
                        .acl("public-read")
                        .build(),
                software.amazon.awssdk.core.sync.RequestBody.fromBytes(audio));

        String publicUrl = String.format(publicUrlBase, audioBucket) + "/" + s3Key;

        VoiceAudioCache entity = VoiceAudioCache.builder()
                .cacheKey(cacheKey)
                .locale(locale)
                .voiceGender(gender)
                .voiceSpeed(speed)
                .renderedText(text)
                .s3Bucket(audioBucket)
                .s3Key(s3Key)
                .publicUrl(publicUrl)
                .bytes(audio.length)
                .lastUsedAt(Instant.now())
                .hitCount(1)
                .build();
        audioCacheRepo.save(entity);

        log.info("[PollyTTS] synthesized locale={} gender={} bytes={} url={}",
                locale, gender, audio.length, publicUrl);
        return publicUrl;
    }

    private static VoiceId pickVoice(LocaleType locale, VoiceGender gender) {
        return switch (locale) {
            case ja_JP -> gender == VoiceGender.FEMALE ? VoiceId.TOMOKO : VoiceId.TAKUMI;
            case en_US -> gender == VoiceGender.FEMALE ? VoiceId.JOANNA : VoiceId.MATTHEW;
            // Polly has no native VN neural voice — fallback to a English voice
            // that reads romaji reasonably. Callers really should use SpeedSMS
            // native TTS for vi_VN; this branch is a safety net only.
            case vi_VN -> gender == VoiceGender.FEMALE ? VoiceId.JOANNA : VoiceId.MATTHEW;
        };
    }

    private static LanguageCode toLanguageCode(LocaleType locale) {
        return switch (locale) {
            case ja_JP -> LanguageCode.JA_JP;
            case en_US -> LanguageCode.EN_US;
            case vi_VN -> LanguageCode.EN_US;
        };
    }

    private static String wrapWithProsody(String text, BigDecimal speed) {
        // Convert 0.80-1.20 → "-20%" to "+20%" for SSML <prosody rate>
        int pct = speed.subtract(BigDecimal.ONE)
                .multiply(BigDecimal.valueOf(100))
                .intValue();
        String rate = (pct >= 0 ? "+" : "") + pct + "%";
        return "<speak><prosody rate=\"" + rate + "\">" + escapeSsml(text) + "</prosody></speak>";
    }

    private static String escapeSsml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private static String hash(String text, LocaleType locale,
                                VoiceGender gender, BigDecimal speed) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(text.getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            md.update(locale.name().getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            md.update(gender.name().getBytes(StandardCharsets.UTF_8));
            md.update((byte) 0);
            md.update(speed.toPlainString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            throw new IllegalStateException("hash failed: " + e.getMessage(), e);
        }
    }
}
