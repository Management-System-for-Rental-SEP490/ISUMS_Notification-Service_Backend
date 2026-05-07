package com.isums.notificationservice.services;

import com.isums.notificationservice.domains.dtos.SpeedSmsVoiceResponse;
import com.isums.notificationservice.infrastructures.abstracts.SmsProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * AWS SNS SMS provider — the only SMS path.
 *
 * <p>Flow:
 * <ol>
 *   <li>Normalise phone to E.164 with leading {@code +} (e.g.
 *       {@code 0326336224} → {@code +84326336224}). Stringee voice
 *       expects no leading {@code +}, so the format diverges by channel.</li>
 *   <li>Set {@code AWS.SNS.SMS.SMSType=Transactional} attribute so SNS
 *       picks the higher-priority delivery path (alerts, not marketing).</li>
 *   <li>Set {@code AWS.SNS.SMS.SenderID} attribute (best-effort —
 *       VN MNOs ignore it but US/EU accept).</li>
 *   <li>{@link SnsClient#publish(PublishRequest)} returns a messageId on
 *       acceptance. Successful API call ≠ guaranteed handset delivery —
 *       sandbox accounts must verify each destination phone first;
 *       production accounts (after AWS approves the Use-Case form) can
 *       send to any number.</li>
 * </ol>
 *
 * <p>Activated by {@code app.notification.sms.provider=AWS_SNS} (the
 * default). {@link Primary} avoids ambiguity should another
 * {@link SmsProvider} bean ever be reintroduced.
 */
@Service
@Primary
@ConditionalOnProperty(name = "app.notification.sms.provider", havingValue = "AWS_SNS")
@Slf4j
public class AwsSnsClient implements SmsProvider {

    private final SnsClient snsClient;

    /**
     * Optional SenderID — appears as the SMS sender on supported countries
     * (US, AU, EU). VN MNOs ignore it and substitute their own short-code,
     * but setting it doesn't hurt. Max 11 alphanumeric characters.
     */
    @Value("${app.notification.aws.sns.sender-id:ISUMS}")
    private String senderId;

    /**
     * Default-CC fallback for phones that come in as 0xxx (VN domestic).
     * Should match the country whose MNO numbers we support.
     */
    @Value("${app.notification.aws.sns.default-cc:84}")
    private String defaultCc;

    @Value("${app.notification.voice.dry-run:false}")
    private boolean dryRun;

    public AwsSnsClient(SnsClient snsClient) {
        this.snsClient = snsClient;
    }

    @jakarta.annotation.PostConstruct
    void logConfig() {
        log.info("[AWS SNS init] senderId={} defaultCc=+{} dryRun={}",
                senderId, defaultCc, dryRun);
    }

    @Override
    public String providerId() {
        return "AWS_SNS";
    }

    @Override
    public SpeedSmsVoiceResponse sendSms(String phone, String text) {
        if (dryRun) {
            log.info("[AWS SNS DRY_RUN] phone={} text={}", phone, text);
            return new SpeedSmsVoiceResponse(true,
                    "dry-sns-" + UUID.randomUUID(),
                    "SENT", null);
        }

        String e164 = toE164Plus(phone, defaultCc);
        if (e164.isBlank()) {
            return new SpeedSmsVoiceResponse(false, null, "FAILED",
                    "Phone number is empty / cannot be normalised to E.164");
        }

        try {
            Map<String, MessageAttributeValue> attrs = new HashMap<>();
            attrs.put("AWS.SNS.SMS.SMSType",
                    MessageAttributeValue.builder()
                            .dataType("String")
                            .stringValue("Transactional")
                            .build());
            if (senderId != null && !senderId.isBlank()) {
                attrs.put("AWS.SNS.SMS.SenderID",
                        MessageAttributeValue.builder()
                                .dataType("String")
                                .stringValue(senderId)
                                .build());
            }

            // SNS limits: GSM-7 ≤ 160 chars or UCS-2 ≤ 70 chars per segment.
            // Vietnamese diacritics force UCS-2 → keep texts short. Truncating
            // here is a last-resort safety; alert templates are already short.
            String body = text == null ? "" : text;
            if (body.length() > 600) {
                log.warn("[AWS SNS] truncating body from {} chars to 600 (3 UCS-2 segments)", body.length());
                body = body.substring(0, 600);
            }

            PublishRequest req = PublishRequest.builder()
                    .phoneNumber(e164)
                    .message(body)
                    .messageAttributes(attrs)
                    .build();

            PublishResponse resp = snsClient.publish(req);
            String messageId = resp.messageId();

            log.info("[AWS SNS] sms sent phone={} messageId={} bytes={}",
                    e164, messageId, body.length());

            return new SpeedSmsVoiceResponse(true,
                    messageId == null ? ("sns-" + System.currentTimeMillis()) : messageId,
                    "SENT", null);
        } catch (software.amazon.awssdk.services.sns.model.InvalidParameterException e) {
            // Common Sandbox failure: destination phone not verified yet.
            log.error("[AWS SNS] invalid parameter phone={} msg={}", e164, e.awsErrorDetails().errorMessage());
            return new SpeedSmsVoiceResponse(false, null, "FAILED",
                    "AWS SNS rejected: " + e.awsErrorDetails().errorMessage()
                            + " (Sandbox accounts must verify the destination phone first)");
        } catch (Exception e) {
            log.error("[AWS SNS] publish failed phone={}: {}", e164, e.getMessage(), e);
            return new SpeedSmsVoiceResponse(false, null, "FAILED", e.getMessage());
        }
    }

    /**
     * Normalises a raw phone string to AWS-SNS-friendly E.164 with a
     * leading {@code +}. Handles {@code 0xxx}, {@code 84xxx},
     * {@code +84xxx}, {@code 0084xxx}, with or without spaces / dashes.
     *
     * <p>Examples (defaultCc = "84"):
     * <ul>
     *   <li>{@code 0326336224}  → {@code +84326336224}</li>
     *   <li>{@code 84326336224} → {@code +84326336224}</li>
     *   <li>{@code +84326336224} → {@code +84326336224}</li>
     *   <li>{@code  032 633-6224} → {@code +84326336224}</li>
     *   <li>{@code 0084326336224} → {@code +84326336224}</li>
     * </ul>
     *
     * <p>Different from {@link StringeeClientImpl#normalizeE164} which
     * returns the country-code-prefixed digits WITHOUT the leading
     * {@code +} (Stringee REST quirk). AWS SNS requires the {@code +}.
     */
    static String toE164Plus(String phone, String defaultCc) {
        if (phone == null) return "";
        String digits = phone.replaceAll("[\\s\\-()]+", "").trim();
        if (digits.isEmpty()) return "";
        if (digits.startsWith("+")) {
            return digits;
        }
        if (digits.startsWith("00")) {
            return "+" + digits.substring(2);
        }
        if (digits.startsWith("0")) {
            return "+" + defaultCc + digits.substring(1);
        }
        // Already CC-prefixed (84xxx) but missing +.
        return "+" + digits;
    }
}
