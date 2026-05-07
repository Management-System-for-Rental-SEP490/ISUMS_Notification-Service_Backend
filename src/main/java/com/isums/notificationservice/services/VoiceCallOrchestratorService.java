package com.isums.notificationservice.services;

import com.isums.notificationservice.domains.dtos.AlertDispatchRequest;
import com.isums.notificationservice.domains.dtos.SpeedSmsVoiceRequest;
import com.isums.notificationservice.domains.dtos.SpeedSmsVoiceResponse;
import com.isums.notificationservice.domains.entities.UserNotificationPreferences;
import com.isums.notificationservice.domains.entities.VoiceCallJob;
import com.isums.notificationservice.domains.enums.LocaleType;
import com.isums.notificationservice.domains.enums.VoiceCallStatus;
import com.isums.notificationservice.infrastructures.abstracts.TtsAudioSynthesizer;
import com.isums.notificationservice.infrastructures.repositories.VoiceCallJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class VoiceCallOrchestratorService {

    private final ChannelTemplateRenderer templateRenderer;
    private final VoiceProviderRouter providerRouter;
    private final TtsAudioSynthesizer ttsAudioSynthesizer;
    private final VoiceCallJobRepository voiceJobRepo;

    @Value("${app.notification.voice.webhook-base-url:https://api-dev.isums.pro}")
    private String webhookBaseUrl;

    @Value("${app.notification.voice.caller-id-name:ISUMS}")
    private String callerIdName;

    @Transactional
    public VoiceCallJob enqueueFirstAttempt(
            UUID userId,
            String phone,
            UserNotificationPreferences prefs,
            AlertDispatchRequest alertReq,
            Map<String, Object> templateVars,
            com.isums.notificationservice.domains.enums.RecipientRole role) {
        return enqueueFirstAttempt(userId, phone, prefs, alertReq, templateVars, role, null);
    }

    @Transactional
    public VoiceCallJob enqueueFirstAttempt(
            UUID userId,
            String phone,
            UserNotificationPreferences prefs,
            AlertDispatchRequest alertReq,
            Map<String, Object> templateVars,
            com.isums.notificationservice.domains.enums.RecipientRole role,
            com.isums.notificationservice.domains.enums.EscalationReason reason) {

        String templateKey = voiceTemplateKey(alertReq.eventType().name(), role, reason);
        String roleFallback = voiceTemplateKey(alertReq.eventType().name(), role, null);
        String tenantFallback = voiceTemplateKey(alertReq.eventType().name(),
                com.isums.notificationservice.domains.enums.RecipientRole.TENANT, null);
        LocaleType locale = prefs.getLanguage();

        var rendered = renderWithFallbackChain(
                java.util.List.of(templateKey, roleFallback, tenantFallback),
                locale, templateVars);

        java.math.BigDecimal alertValue = alertReq.value() == null ? null
                : java.math.BigDecimal.valueOf(alertReq.value());

        VoiceCallJob job = VoiceCallJob.builder()
                .userId(userId)
                .alertId(alertReq.alertId())
                .eventType(alertReq.eventType().name())
                .phone(phone)
                .locale(locale)
                .templateId(rendered.version().getTemplate().getId())
                .templateVersionId(rendered.version().getId())
                .renderedText(rendered.body())
                .maxAttempts(prefs.getVoiceMaxRetries() + 1)
                .houseId(alertReq.houseId())
                .areaId(alertReq.areaId())
                .areaName(alertReq.areaName())
                .thing(alertReq.thing())
                .metric(alertReq.metric())
                .alertValue(alertValue)
                .alertUnit(alertReq.unit())
                .build();
        job = voiceJobRepo.save(job);

        boolean interactive = role == com.isums.notificationservice.domains.enums.RecipientRole.TENANT;
        dial(job, rendered.body(), locale, prefs, interactive);
        return job;
    }

    @Transactional
    public void dial(VoiceCallJob job, String textBody, LocaleType locale,
                      UserNotificationPreferences prefs) {
        dial(job, textBody, locale, prefs, true);
    }

    @Transactional
    public void dial(VoiceCallJob job, String textBody, LocaleType locale,
                      UserNotificationPreferences prefs, boolean interactive) {
        var provider = providerRouter.voice();
        String audioUrl = null;
        String tts = textBody;

        if (locale != LocaleType.vi_VN) {
            try {
                audioUrl = ttsAudioSynthesizer.synthesizeAndCache(
                        textBody, locale,
                        prefs.getVoiceGender(), prefs.getVoiceSpeed());
            } catch (Exception e) {
                log.warn("[Voice] Polly synth failed, falling back to VN TTS: {}", e.getMessage());
            }
        }

        String voiceName = null;
        var voiceProvider = provider;
        if (voiceProvider instanceof StringeeClientImpl s) {
            voiceName = s.voiceForLocale(locale);
        }

        SpeedSmsVoiceRequest req = new SpeedSmsVoiceRequest(
                job.getPhone(),
                tts,
                audioUrl,
                2,
                webhookBaseUrl + "/api/notifications/voice/webhook",
                callerIdName,
                job.getId(),
                voiceName,
                interactive
        );

        SpeedSmsVoiceResponse resp = provider.sendVoiceCall(req);
        log.info("[Voice] dial provider={} jobId={} status={} callId={}",
                provider.providerId(), job.getId(), resp.status(), resp.callId());

        if (resp.ok()) {
            job.setProviderCallId(resp.callId());
            job.setStatus(VoiceCallStatus.DIALING);
        } else {
            job.setStatus(VoiceCallStatus.FAILED);
            job.setErrorMessage(resp.errorMessage());
        }
        voiceJobRepo.save(job);
    }

    private com.isums.notificationservice.services.ChannelTemplateRenderer.RenderedTemplate renderWithFallbackChain(
            java.util.List<String> keys,
            LocaleType locale,
            Map<String, Object> templateVars) {
        RuntimeException lastError = null;
        java.util.LinkedHashSet<String> uniq = new java.util.LinkedHashSet<>(keys);
        for (String key : uniq) {
            try {
                return templateRenderer.render(
                        key,
                        com.isums.notificationservice.domains.enums.NotificationChannel.VOICE,
                        locale, templateVars);
            } catch (RuntimeException e) {
                lastError = e;
                log.warn("[Voice] template {} not renderable for locale={} ({}), trying next",
                        key, locale, e.getMessage());
            }
        }
        throw lastError != null ? lastError
                : new IllegalStateException("no template keys resolved: " + keys);
    }

    private static String voiceTemplateKey(String eventType) {
        return voiceTemplateKey(eventType,
                com.isums.notificationservice.domains.enums.RecipientRole.TENANT, null);
    }

    private static String voiceTemplateKey(String eventType,
                                              com.isums.notificationservice.domains.enums.RecipientRole role) {
        return voiceTemplateKey(eventType, role, null);
    }

    private static String voiceTemplateKey(String eventType,
                                              com.isums.notificationservice.domains.enums.RecipientRole role,
                                              com.isums.notificationservice.domains.enums.EscalationReason reason) {
        String base = "voice_" + eventType.toLowerCase();
        String roleKey = switch (role) {
            case MANAGER, LANDLORD -> base + "_manager";
            case TENANT             -> base;
        };
        if (reason == com.isums.notificationservice.domains.enums.EscalationReason.NO_ANSWER_MAX_RETRIES
                && (role == com.isums.notificationservice.domains.enums.RecipientRole.MANAGER
                    || role == com.isums.notificationservice.domains.enums.RecipientRole.LANDLORD)) {
            return roleKey + "_noanswer";
        }
        return roleKey;
    }
}
