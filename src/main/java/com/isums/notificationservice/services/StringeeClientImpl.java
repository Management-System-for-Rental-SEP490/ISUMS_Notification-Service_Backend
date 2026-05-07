package com.isums.notificationservice.services;

import com.isums.notificationservice.domains.dtos.SpeedSmsVoiceRequest;
import com.isums.notificationservice.domains.dtos.SpeedSmsVoiceResponse;
import com.isums.notificationservice.infrastructures.abstracts.VoiceProvider;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class StringeeClientImpl implements VoiceProvider {

    private final RestClient stringeeRestClient;
    private final ObjectMapper objectMapper;

    @Value("${app.notification.stringee.api-key-sid:}")
    private String apiKeySid;

    @Value("${app.notification.stringee.api-key-secret:}")
    private String apiKeySecret;

    @Value("${app.notification.stringee.from-number:}")
    private String fromNumber;

    @Value("${app.notification.stringee.callout-path:/v1/call2/callout}")
    private String calloutPath;

    @Value("${app.notification.stringee.voice-name:vietnam_female}")
    private String defaultVoiceName;

    @Value("${app.notification.stringee.voice-vi:vietnam_female}")
    private String voiceVi;

    @Value("${app.notification.stringee.voice-en:english_female}")
    private String voiceEn;

    @Value("${app.notification.stringee.voice-ja:japanese_female}")
    private String voiceJa;

    @Value("${app.notification.stringee.answer-url-base:https://api-dev.isums.pro}")
    private String answerUrlBase;

    @Value("${app.notification.stringee.sms-path:/v1/sms}")
    private String smsPath;

    @Value("${app.notification.stringee.sms-from:ISUMS}")
    private String smsFrom;

    @Value("${app.notification.voice.dry-run:false}")
    private boolean dryRun;

    public StringeeClientImpl(RestClient stringeeRestClient, ObjectMapper objectMapper) {
        this.stringeeRestClient = stringeeRestClient;
        this.objectMapper = objectMapper;
    }

    @jakarta.annotation.PostConstruct
    void logConfig() {
        log.info("[Stringee init] apiKeySid={} from={} calloutPath={} voice={} dryRun={}",
                apiKeySid, fromNumber, calloutPath, defaultVoiceName, dryRun);
    }

    @Override
    public String providerId() { return "STRINGEE"; }

    @Override
    public SpeedSmsVoiceResponse sendVoiceCall(SpeedSmsVoiceRequest request) {
        if (dryRun) {
            String fakeId = "dry-stringee-" + UUID.randomUUID();
            log.info("[Stringee DRY_RUN] callout to={} text=\n{}", request.phone(), request.tts());
            return new SpeedSmsVoiceResponse(true, fakeId, "DIALING", null);
        }

        if (apiKeySid == null || apiKeySid.isBlank()
                || apiKeySecret == null || apiKeySecret.isBlank()) {
            return new SpeedSmsVoiceResponse(false, null, "FAILED",
                    "Stringee credentials missing (apiKeySid / apiKeySecret)");
        }

        try {
            String jwt = buildRestJwt();
            Map<String, Object> body = buildSccoBody(request);

            String rawResponse = stringeeRestClient.post()
                    .uri(calloutPath)
                    .header("X-STRINGEE-AUTH", jwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            log.info("[Stringee] callout response phone={} raw={}", request.phone(), rawResponse);

            JsonNode json = objectMapper.readTree(rawResponse == null ? "{}" : rawResponse);
            int r = json.path("r").asInt(-1);
            String callId = json.path("call_id").asString();
            if (r != 0 && r != 13) {
                String msg = json.path("message").asString();
                if (msg == null || msg.isBlank()) msg = rawResponse;
                return new SpeedSmsVoiceResponse(false, null, "FAILED", msg);
            }
            return new SpeedSmsVoiceResponse(true, callId, "DIALING", null);
        } catch (Exception e) {
            log.error("[Stringee] callout failed phone={}: {}",
                    request.phone(), e.getMessage(), e);
            return new SpeedSmsVoiceResponse(false, null, "FAILED", e.getMessage());
        }
    }

    private Map<String, Object> buildSccoBody(SpeedSmsVoiceRequest request) {
        Map<String, Object> from = new HashMap<>();
        from.put("type", "external");
        from.put("number", normalizeE164(fromNumber, "84"));
        from.put("alias", "ISUMS");

        Map<String, Object> to = new HashMap<>();
        to.put("type", "external");
        to.put("number", normalizeE164(request.phone(), "84"));
        to.put("alias", "tenant");

        Map<String, Object> body = new HashMap<>();
        body.put("from", from);
        body.put("to", List.of(to));

        String text = request.tts();
        if (text == null || text.isBlank()) text = "Cảnh báo từ hệ thống ISUMS.";
        int loop = Math.max(1, request.loop());

        String voice = request.voiceName();
        if (voice == null || voice.isBlank()) voice = defaultVoiceName;

        java.util.List<Map<String, Object>> actions = new java.util.ArrayList<>();
        Map<String, Object> talk = new HashMap<>();
        talk.put("action", "talk");
        talk.put("text", text);
        talk.put("voice", voice);
        talk.put("loop", loop);              // body repeats N times — see comment
        talk.put("bargeIn", true);
        talk.put("silenceTime", 0);
        actions.add(talk);
        // SCCO branches by `interactive` flag from caller:
        //   - TENANT path (interactive=true): full escalation flow with
        //     `input` action capturing DTMF + a trailing `talk` ack so
        //     the user hears "Đã chuyển cho quản lý" before hangup.
        //     Stringee trial DOES double the disclaimer here (once per
        //     talk action), trade-off accepted because tenant explicitly
        //     wanted audible confirmation that escalation succeeded.
        //   - MANAGER / LANDLORD path (interactive=false): alert + hangup.
        //     No DTMF prompts, no ack — manager IS the recipient, asking
        //     them to "press 2 to forward to manager" makes no sense; the
        //     escalation chain stops at manager (or chain step manager →
        //     landlord if the manager doesn't pick up — handled separately
        //     in VoiceWebhookHandler.scheduleRetryOrEscalate).
        if (request.interactive()) {
            // Short timeOut — TTS already played twice with bargeIn=true,
            // so anyone who wants to press 2 has had ~60s of opportunity
            // already. After TTS completes, give just 5 seconds for a
            // late press, then move on to ack+hangup. Prevents the call
            // from "hanging" silently after the message ends.
            Map<String, Object> input = new HashMap<>();
            input.put("action", "input");
            input.put("maxDigits", 1);
            input.put("timeOut", 5);
            input.put("submitOnHash", false);
            input.put("eventUrl",
                    answerUrlBase + "/api/notifications/voice/stringee-answer-url");
            actions.add(input);

            Map<String, Object> ack = new HashMap<>();
            ack.put("action", "talk");
            ack.put("text", "Đã chuyển cho quản lý.");
            ack.put("voice", voice);
            ack.put("silenceTime", 0);
            actions.add(ack);
        }

        actions.add(Map.of("action", "hangup"));
        body.put("actions", actions);

        // customField is echoed back to eventUrl + answer_url callbacks
        // (Stringee SCCO docs name it `customField`, not `custom_data`).
        // Useful for joining Stringee call_id ↔ our voice_call_jobs row
        // when the project-level Answer URL fires.
        String jobId = request.jobId() == null ? "" : request.jobId().toString();
        body.put("customField", jobId);
        body.put("custom_data", jobId);  // legacy key kept for back-compat

        // answer_url retained as a Stringee fallback path; Project-level
        // Answer URL on the Console points to the same endpoint.
        body.put("answer_url",
                answerUrlBase + "/api/notifications/voice/stringee-answer-url?jobId=" + jobId);
        return body;
    }

    /**
     * Normalises a phone string to E.164 (no leading {@code +}) for Stringee.
     *
     * <p>Accepts the formats VN users actually type into the app:
     * <ul>
     *   <li>{@code 0326336224} → {@code 84326336224} (drop leading 0, prefix country)</li>
     *   <li>{@code +84326336224} / {@code 0084326336224} → {@code 84326336224}</li>
     *   <li>{@code 84326336224} → unchanged</li>
     *   <li>foreign (eg {@code +14155551234}) → {@code 14155551234}</li>
     * </ul>
     * Whitespace, dashes and parentheses are stripped before parsing.
     * Returns the input verbatim if normalisation can't be inferred — Stringee
     * will then reject and we'll see the raw value in the failure log.
     *
     * @param phone        raw phone string from User-Service / DB
     * @param defaultCc    country code to apply when input starts with {@code 0}
     */
    static String normalizeE164(String phone, String defaultCc) {
        if (phone == null) return "";
        String digits = phone.replaceAll("[\\s\\-()]+", "").trim();
        if (digits.isEmpty()) return "";
        if (digits.startsWith("+")) {
            return digits.substring(1);
        }
        if (digits.startsWith("00")) {
            return digits.substring(2);
        }
        if (digits.startsWith("0")) {
            return defaultCc + digits.substring(1);
        }
        // Already CC-prefixed (or unknown shape — pass through best-effort).
        return digits;
    }

    private String buildRestJwt() throws JOSEException {
        long nowEpoch = Instant.now().getEpochSecond();
        long expEpoch = nowEpoch + 3600;
        String jti = apiKeySid + "-" + nowEpoch;

        Map<String, Object> claims = new HashMap<>();
        claims.put("jti", jti);
        claims.put("iss", apiKeySid);
        claims.put("exp", expEpoch);
        claims.put("rest_api", true);

        // Stringee requires "cty":"stringee-api;v=1" in JWT header. Nimbus
        // reserves "cty" as a registered claim → must use .contentType().
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256)
                .type(new com.nimbusds.jose.JOSEObjectType("JWT"))
                .contentType("stringee-api;v=1")
                .build();
        JWSObject jws = new JWSObject(header,
                new Payload(objectMapper.writeValueAsString(claims)));
        jws.sign(new MACSigner(apiKeySecret.getBytes(StandardCharsets.UTF_8)));
        return jws.serialize();
    }

    @Override
    public boolean verifyWebhookSignature(String rawBody, String signature) {
        // Stringee event webhooks are NOT signed — they rely on the
        // event_url being a hard-to-guess HTTPS URL on your project.
        // For thesis demo we accept all + log; production should verify
        // by fetching the call detail back via REST and matching call_id.
        if (dryRun) return true;
        return true;
    }

    /**
     * Resolves the Stringee {@code voice} parameter for a given locale.
     * Public so the orchestrator can stamp the right voice into the
     * request DTO before calling.
     */
    public String voiceForLocale(com.isums.notificationservice.domains.enums.LocaleType locale) {
        if (locale == null) return defaultVoiceName;
        return switch (locale) {
            case vi_VN -> voiceVi;
            case en_US -> voiceEn;
            case ja_JP -> voiceJa;
        };
    }

}
