package com.isums.notificationservice.services;

import com.isums.notificationservice.domains.dtos.SpeedSmsVoiceRequest;
import com.isums.notificationservice.domains.dtos.SpeedSmsVoiceResponse;
import com.isums.notificationservice.infrastructures.abstracts.VoiceProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.pinpointsmsvoicev2.PinpointSmsVoiceV2Client;
import software.amazon.awssdk.services.pinpointsmsvoicev2.model.SendVoiceMessageRequest;
import software.amazon.awssdk.services.pinpointsmsvoicev2.model.SendVoiceMessageResponse;
import software.amazon.awssdk.services.pinpointsmsvoicev2.model.VoiceMessageBodyTextType;

import java.util.UUID;

@Service
@Slf4j
public class AwsPinpointVoiceClient implements VoiceProvider {

    private final PinpointSmsVoiceV2Client client;

    @Value("${app.notification.aws.voice.origination-identity:}")
    private String originationIdentity;

    @Value("${app.notification.aws.voice.voice-id:Lan}")
    private String voiceId;

    @Value("${app.notification.aws.voice.body-type:TEXT}")
    private String bodyType;

    @Value("${app.notification.aws.voice.configuration-set:}")
    private String configurationSetName;

    @Value("${app.notification.aws.voice.max-price-per-minute:0.50}")
    private String maxPricePerMinute;

    @Value("${app.notification.voice.dry-run:false}")
    private boolean dryRun;

    public AwsPinpointVoiceClient(PinpointSmsVoiceV2Client client) {
        this.client = client;
    }

    @jakarta.annotation.PostConstruct
    void logConfig() {
        log.info("[AwsPinpointVoice init] origination={} voiceId={} bodyType={} dryRun={}",
                originationIdentity, voiceId, bodyType, dryRun);
    }

    @Override
    public String providerId() { return "AWS_PINPOINT"; }

    @Override
    public SpeedSmsVoiceResponse sendVoiceCall(SpeedSmsVoiceRequest request) {
        if (dryRun) {
            String fakeId = "dry-aws-" + UUID.randomUUID();
            log.info("[AwsPinpointVoice DRY_RUN] callout to={} text=\n{}", request.phone(), request.tts());
            return new SpeedSmsVoiceResponse(true, fakeId, "DIALING", null);
        }

        if (originationIdentity == null || originationIdentity.isBlank()) {
            return new SpeedSmsVoiceResponse(false, null, "FAILED",
                    "AWS Pinpoint origination-identity not configured");
        }

        try {
            VoiceMessageBodyTextType bodyTypeEnum = "SSML".equalsIgnoreCase(bodyType)
                    ? VoiceMessageBodyTextType.SSML
                    : VoiceMessageBodyTextType.TEXT;

            SendVoiceMessageRequest.Builder reqBuilder = SendVoiceMessageRequest.builder()
                    .destinationPhoneNumber(request.phone())
                    .originationIdentity(originationIdentity)
                    .messageBody(request.tts())
                    .messageBodyTextType(bodyTypeEnum)
                    .voiceId(voiceId)
                    .maxPricePerMinute(maxPricePerMinute);

            if (configurationSetName != null && !configurationSetName.isBlank()) {
                reqBuilder.configurationSetName(configurationSetName);
            }

            SendVoiceMessageResponse resp = client.sendVoiceMessage(reqBuilder.build());
            String messageId = resp.messageId();
            log.info("[AwsPinpointVoice] sent phone={} messageId={}", request.phone(), messageId);
            return new SpeedSmsVoiceResponse(true, messageId, "DIALING", null);
        } catch (Exception e) {
            log.error("[AwsPinpointVoice] failed phone={}: {}", request.phone(), e.getMessage(), e);
            return new SpeedSmsVoiceResponse(false, null, "FAILED", e.getMessage());
        }
    }

    @Override
    public boolean verifyWebhookSignature(String rawBody, String signature) {
        if (dryRun) return true;
        return true;
    }
}
