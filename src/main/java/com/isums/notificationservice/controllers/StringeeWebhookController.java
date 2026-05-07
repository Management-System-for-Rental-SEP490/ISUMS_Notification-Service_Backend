package com.isums.notificationservice.controllers;

import com.isums.notificationservice.domains.dtos.ApiResponse;
import com.isums.notificationservice.domains.dtos.ApiResponses;
import com.isums.notificationservice.domains.dtos.SpeedSmsWebhookPayload;
import com.isums.notificationservice.services.VoiceWebhookHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Stringee event_url callback — Stringee posts call status events
 * (started, ringing, answered, ended, dtmf) to whatever URL is set on
 * the project in the Stringee Console.
 *
 * <p>Payload shape differs from SpeedSMS:
 * {@code {"event":"answered","call_id":"...","duration":12,"dtmf":"1",...}}
 *
 * <p>We map the relevant subset onto the existing
 * {@link VoiceWebhookHandler}'s state machine so retry / escalation /
 * DTMF opt-out behave the same regardless of provider.
 */
@RestController
@RequestMapping("/api/notifications/voice")
@RequiredArgsConstructor
@Slf4j
public class StringeeWebhookController {

    private final VoiceWebhookHandler webhookHandler;
    private final ObjectMapper objectMapper;

    @PostMapping(value = "/stringee-webhook", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> webhook(@RequestBody String rawBody) {
        log.info("[StringeeWebhook] received body={}", rawBody);
        try {
            JsonNode json = objectMapper.readTree(rawBody);

            // Stringee event payload (observed in production traffic):
            //   {"call_status":"created"|"ringing"|"answered"|"ended"|"dtmf_received",
            //    "call_id":"...",
            //    "duration":43,
            //    "answerDuration":37,
            //    "endCallCause":"USER_END_CALL",
            //    "endedBy":"EXTERNAL",
            //    "digit":"1"          // only on dtmf_received
            //   }
            // tools.jackson `asString()` throws on MissingNode — use the
            // overload with a default value so absent fields don't crash.
            String status = firstNonBlank(
                    json.path("call_status").asString(""),
                    json.path("event").asString(""));
            String callId = json.path("call_id").asString("");
            String dtmf   = firstNonBlank(
                    json.path("digit").asString(""),
                    json.path("dtmf").asString(""),
                    json.path("digits").asString(""));
            int duration       = json.path("duration").asInt(0);
            int answerDuration = json.path("answerDuration").asInt(0);
            String endCause    = json.path("endCallCause").asString("");

            String mappedStatus = mapStatus(status, answerDuration, endCause);

            // Created/ringing → no DB state change (job already DIALING).
            if ("DIALING".equals(mappedStatus) && (dtmf == null || dtmf.isBlank())) {
                return ResponseEntity.ok(ApiResponses.ok(
                        Map.of("processed", false,
                                "noop", true,
                                "status", status,
                                "callId", callId),
                        "ignored"));
            }

            SpeedSmsWebhookPayload mapped = new SpeedSmsWebhookPayload(
                    callId, mappedStatus,
                    duration > 0 ? duration : answerDuration,
                    null, dtmf, null, null, null);
            var job = webhookHandler.handle(mapped);

            return ResponseEntity.ok(ApiResponses.ok(
                    Map.of("processed", job.isPresent(),
                            "status", status,
                            "mapped", mappedStatus,
                            "callId", callId,
                            "dtmf", dtmf == null ? "" : dtmf),
                    "OK"));
        } catch (Exception e) {
            log.error("[StringeeWebhook] handle failed: {}", e.getMessage(), e);
            return ResponseEntity.ok(ApiResponses.ok(
                    Map.of("processed", false, "error", e.getMessage()),
                    "Logged"));
        }
    }

    /**
     * Maps Stringee call_status onto our internal VoiceCallStatus enum.
     * "ended" is special: distinguishes ANSWERED-then-completed vs
     * NO_ANSWER (call rang but never picked up) using answerDuration.
     */
    private static String mapStatus(String stringeeStatus, int answerDuration, String endCause) {
        return switch (stringeeStatus == null ? "" : stringeeStatus.toLowerCase()) {
            case "answered"      -> "ANSWERED";
            case "dtmf_received" -> "ANSWERED";
            case "no_answer"     -> "NO_ANSWER";
            case "busy"          -> "BUSY";
            case "failed", "error" -> "FAILED";
            case "ended" -> answerDuration > 0 ? "ANSWERED" : "NO_ANSWER";
            default -> "DIALING";
        };
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v;
        return "";
    }
}
