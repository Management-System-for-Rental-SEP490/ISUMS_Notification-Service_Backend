package com.isums.notificationservice.controllers;

import com.isums.notificationservice.domains.dtos.SpeedSmsWebhookPayload;
import com.isums.notificationservice.domains.entities.VoiceCallJob;
import com.isums.notificationservice.infrastructures.repositories.VoiceCallJobRepository;
import com.isums.notificationservice.services.VoiceWebhookHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stringee project-level Answer URL endpoint.
 *
 * <p>Stringee trial accounts (observed empirically) <b>do not honour the
 * per-action {@code event_url}</b> on inline gather actions — DTMF digits
 * are dropped instead of POSTed. The only event channel that fires
 * reliably is the project Answer URL configured on console.stringee.com.
 * That URL receives BOTH:
 * <ol>
 *   <li>Initial fetch when the call connects → return [talk, gather] SCCO.</li>
 *   <li>Gather callback when the user presses a digit → request includes
 *       {@code digit}/{@code digits} param or body field. We route through
 *       {@link VoiceWebhookHandler} (same DTMF state machine as production)
 *       and return [talk("Đã chuyển..."), hangup] SCCO.</li>
 * </ol>
 *
 * <p>For paid Stringee plans where per-action event_url works,
 * {@code /stringee-gather} is still wired — both paths converge on the
 * same handler.
 */
@RestController
@RequestMapping("/api/notifications/voice")
@RequiredArgsConstructor
@Slf4j
public class StringeeAnswerUrlController {

    private final VoiceCallJobRepository voiceJobRepo;
    private final VoiceWebhookHandler webhookHandler;
    private final ObjectMapper objectMapper;

    @Value("${app.notification.stringee.voice-name:vietnam_female}")
    private String voiceName;

    /** Public base for Stringee callbacks — must be reachable from Stringee servers. */
    @Value("${app.notification.stringee.answer-url-base:https://api-dev.isums.pro}")
    private String answerUrlBase;

    @RequestMapping(value = "/stringee-answer-url",
            method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<List<Map<String, Object>>> answerUrl(
            @RequestParam(value = "jobId", required = false) String jobIdParam,
            @RequestParam(value = "customField", required = false) String customField,
            @RequestParam(value = "custom_data", required = false) String customData,
            @RequestParam(value = "call_id", required = false) String callIdParam,
            @RequestParam(value = "dtmf", required = false) String dtmfParam,
            @RequestParam Map<String, String> allParams,
            @RequestBody(required = false) String rawBody) {

        log.info("[StringeeAnswerUrl] hit jobId={} customField={} customData={} callId={} dtmf={} params={} body={}",
                jobIdParam, customField, customData, callIdParam, dtmfParam, allParams, rawBody);

        // Stringee `input` action POSTs JSON like:
        //   {"time":"...", "dtmf":"2", "call_id":"...",
        //    "customField":"...", "timeout":false}
        // Project-level Answer URL (initial fetch) sends GET/POST with
        // call_id in query/body but NO `dtmf`. We branch on dtmf presence.
        String bodyCallId = "";
        String bodyDtmf   = "";
        String bodyCustom = "";
        if (rawBody != null && !rawBody.isBlank()) {
            try {
                JsonNode json = objectMapper.readTree(rawBody);
                bodyCallId = json.path("call_id").asString("");
                bodyDtmf   = json.path("dtmf").asString("");
                bodyCustom = json.path("customField").asString("");
            } catch (Exception e) {
                log.warn("[StringeeAnswerUrl] body parse failed: {}", e.getMessage());
            }
        }

        String dtmf   = firstNonBlank(dtmfParam, bodyDtmf);
        String callId = firstNonBlank(callIdParam, bodyCallId);

        // ── Branch 1: input action callback (digit pressed) ───────────
        // Stringee's `input` action POSTs the DTMF digit here as a one-way
        // notification — it does NOT consume our response body to alter
        // the call flow (verified empirically: returning SCCO arrays here
        // is ignored). The user-facing ack ("Đã ghi nhận...") is part of
        // the ORIGINAL SCCO trailing-talk that runs after input completes.
        // Our only job here is to fire the BE-side escalation/quota state
        // machine and 200 OK so Stringee marks the input event delivered.
        if (dtmf != null && !dtmf.isBlank()) {
            log.info("[StringeeAnswerUrl] DTMF received callId={} digit={}", callId, dtmf);
            try {
                webhookHandler.handle(new SpeedSmsWebhookPayload(
                        callId, "ANSWERED", null, null,
                        dtmf, null, null, null));
            } catch (Exception e) {
                log.error("[StringeeAnswerUrl] DTMF handler failed callId={} digit={}: {}",
                        callId, dtmf, e.getMessage(), e);
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(List.of());
        }

        // ── Branch 2: Initial answer URL fetch — return alert SCCO ───
        // Project-level answer URL doesn't include our jobId query param;
        // Stringee echoes the callout's customField instead. Prefer jobId
        // (per-call answer URL with explicit param) and fall back to
        // customField (or legacy custom_data) which we always set to the
        // same UUID at callout time.
        String resolvedId = firstNonBlank(jobIdParam, customField, bodyCustom, customData);

        String text = "Canh bao tu he thong ISUMS.";
        if (resolvedId != null && !resolvedId.isBlank()) {
            try {
                VoiceCallJob job = voiceJobRepo.findById(UUID.fromString(resolvedId)).orElse(null);
                if (job != null && job.getRenderedText() != null && !job.getRenderedText().isBlank()) {
                    text = job.getRenderedText();
                }
            } catch (IllegalArgumentException e) {
                log.warn("[StringeeAnswerUrl] invalid id={}", resolvedId);
            }
        }

        // SCCO: [talk(TTS), input, talk(ack), hangup]. The `input` action
        // notifies eventUrl with the DTMF digit (BE fires escalation in
        // Branch 1) but Stringee proceeds to the NEXT action regardless
        // of what eventUrl response says. The trailing talk(ack) + hangup
        // guarantees the user hears confirmation before the call ends.
        List<Map<String, Object>> scco = new java.util.ArrayList<>();

        Map<String, Object> talk = new HashMap<>();
        talk.put("action", "talk");
        talk.put("text", text);
        talk.put("voice", voiceName);
        scco.add(talk);

        Map<String, Object> input = new HashMap<>();
        input.put("action", "input");
        input.put("maxDigits", 1);
        input.put("timeOut", 30);
        input.put("submitOnHash", false);
        input.put("eventUrl",
                answerUrlBase + "/api/notifications/voice/stringee-answer-url");
        scco.add(input);

        // No trailing talk — Stringee trial prepends the trial-account
        // disclaimer before every talk action, so an ack talk would
        // duplicate the disclaimer and stretch the call into billable
        // dead air (see StringeeClientImpl.buildSccoBody for full notes).
        // Hangup right after input keeps the call short and clean.
        scco.add(Map.of("action", "hangup"));

        // Force JSON Content-Type so Stringee parses correctly regardless
        // of what Accept header it sent.
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(scco);
    }


    private static String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String v : values) if (v != null && !v.isBlank()) return v;
        return "";
    }
}
