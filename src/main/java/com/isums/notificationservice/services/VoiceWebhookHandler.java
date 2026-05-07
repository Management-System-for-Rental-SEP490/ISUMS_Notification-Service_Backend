package com.isums.notificationservice.services;

import com.isums.notificationservice.domains.dtos.AlertDispatchRequest;
import com.isums.notificationservice.domains.dtos.SpeedSmsWebhookPayload;
import com.isums.notificationservice.domains.entities.NotificationSubscription;
import com.isums.notificationservice.domains.entities.UserNotificationPreferences;
import com.isums.notificationservice.domains.entities.VoiceCallJob;
import com.isums.notificationservice.domains.enums.AlertEventType;
import com.isums.notificationservice.domains.enums.EscalationReason;
import com.isums.notificationservice.domains.enums.RecipientRole;
import com.isums.notificationservice.domains.enums.SubscriptionTier;
import com.isums.notificationservice.domains.enums.VoiceCallStatus;
import com.isums.notificationservice.infrastructures.repositories.NotificationSubscriptionRepository;
import com.isums.notificationservice.infrastructures.repositories.UserNotificationPreferencesRepository;
import com.isums.notificationservice.infrastructures.repositories.VoiceCallJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Applies SpeedSMS webhook state to an existing voice_call_jobs row.
 * Drives retry / escalation / DTMF opt-out accordingly.
 */
@Service
@Slf4j
public class VoiceWebhookHandler {

    private final VoiceCallJobRepository voiceJobRepo;
    private final UserNotificationPreferencesRepository prefsRepo;
    private final NotificationSubscriptionRepository subsRepo;
    private final EscalationService escalationService;
    private final NotificationDispatchService dispatchService;

    public VoiceWebhookHandler(VoiceCallJobRepository voiceJobRepo,
                                 UserNotificationPreferencesRepository prefsRepo,
                                 NotificationSubscriptionRepository subsRepo,
                                 EscalationService escalationService,
                                 // @Lazy breaks the circular DispatchService → VoiceCallOrchestrator
                                 // → (eventually back to webhook handler) bean dependency.
                                 @Lazy NotificationDispatchService dispatchService) {
        this.voiceJobRepo = voiceJobRepo;
        this.prefsRepo = prefsRepo;
        this.subsRepo = subsRepo;
        this.escalationService = escalationService;
        this.dispatchService = dispatchService;
    }

    @Transactional
    public Optional<VoiceCallJob> handle(SpeedSmsWebhookPayload payload) {
        if (payload == null || payload.callId() == null || payload.callId().isBlank()) {
            log.warn("[Webhook] empty callId in payload");
            return Optional.empty();
        }

        VoiceCallJob job = voiceJobRepo.findByProviderCallId(payload.callId()).orElse(null);
        if (job == null) {
            log.warn("[Webhook] unknown callId={}", payload.callId());
            return Optional.empty();
        }

        String providerStatus = payload.status() == null ? "" : payload.status().toUpperCase();
        VoiceCallStatus newStatus = mapStatus(providerStatus);

        job.setStatus(newStatus);
        if (payload.duration() != null)    job.setDurationSec(payload.duration());
        if (payload.cost() != null)        job.setCostVnd(payload.cost());
        if (payload.recordingUrl() != null) job.setRecordingUrl(payload.recordingUrl());
        if (payload.errorMessage() != null) job.setErrorMessage(payload.errorMessage());

        if (payload.dtmf() != null && !payload.dtmf().isBlank()) {
            job.setDtmfReceived(payload.dtmf());
            applyDtmf(job, payload.dtmf());
        }

        if (newStatus == VoiceCallStatus.ANSWERED && job.getDtmfReceived() == null) {
            // Answered but nothing pressed → still counts as delivered.
            // No retry needed, no escalation triggered. User heard it.
            job.setAcknowledgedAt(Instant.now());
            job.setStatus(VoiceCallStatus.ACKNOWLEDGED);
        }

        if (newStatus == VoiceCallStatus.NO_ANSWER || newStatus == VoiceCallStatus.BUSY) {
            scheduleRetryOrEscalate(job);
        }

        voiceJobRepo.save(job);
        log.info("[Webhook] callId={} status={} dtmf={} duration={} cost={}",
                payload.callId(), newStatus, payload.dtmf(),
                payload.duration(), payload.cost());
        return Optional.of(job);
    }

    private void applyDtmf(VoiceCallJob job, String dtmf) {
        switch (dtmf.trim()) {
            case "1" -> {
                // Explicit acknowledgement — stop retries.
                job.setAcknowledgedAt(Instant.now());
                job.setStatus(VoiceCallStatus.ACKNOWLEDGED);
            }
            case "2" -> {
                // User explicitly asked to forward to landlord/manager.
                UserNotificationPreferences prefs = prefsRepo.findById(job.getUserId()).orElse(null);
                if (prefs == null) {
                    log.warn("[Webhook] escalation requested but no prefs for userId={}", job.getUserId());
                    return;
                }
                UUID target = escalationService.resolveEscalationTarget(
                        job.getUserId(), prefs, job.getHouseId());
                if (target == null) {
                    log.warn("[Webhook] escalation requested but no target for userId={}", job.getUserId());
                    return;
                }
                escalationService.record(job.getId(), null, target, EscalationReason.DTMF_KEY_2);
                triggerEscalationDispatch(job, target, RecipientRole.MANAGER, EscalationReason.DTMF_KEY_2);
                job.setStatus(VoiceCallStatus.ESCALATED);
            }
            case "9" -> {
                // Opt-out — flip voice off in preferences.
                prefsRepo.findById(job.getUserId()).ifPresent(p -> {
                    p.setVoiceEnabled(false);
                    prefsRepo.save(p);
                });
                job.setStatus(VoiceCallStatus.ACKNOWLEDGED);
                log.info("[Webhook] userId={} opted out via DTMF=9", job.getUserId());
            }
            default -> {
                // Unknown digit — treat as acknowledged but log for admin review.
                job.setAcknowledgedAt(Instant.now());
                job.setStatus(VoiceCallStatus.ACKNOWLEDGED);
                log.info("[Webhook] userId={} unknown dtmf={} → treated as ack", job.getUserId(), dtmf);
            }
        }
    }

    private void scheduleRetryOrEscalate(VoiceCallJob job) {
        UserNotificationPreferences prefs = prefsRepo.findById(job.getUserId()).orElse(null);
        NotificationSubscription sub      = subsRepo.findById(job.getUserId()).orElse(null);

        // Tier downgrade mid-retry: stop bothering — the user is no longer
        // paying for voice. Already-used quota is a sunk cost.
        if (sub != null && sub.getTier() != SubscriptionTier.PREMIUM) {
            job.setStatus(VoiceCallStatus.SKIPPED);
            return;
        }

        int attemptNumber = job.getAttemptNumber();
        int maxAttempts   = job.getMaxAttempts();

        if (prefs != null && attemptNumber < maxAttempts) {
            int delaySec = prefs.getVoiceRetryIntervalSec();
            job.setNextRetryAt(Instant.now().plusSeconds(delaySec));
            // Status stays NO_ANSWER until the retry scheduler picks it up.
            log.info("[Webhook] will retry callId={} attempt={}/{} in {}s",
                    job.getProviderCallId(), attemptNumber, maxAttempts, delaySec);
            return;
        }

        // Max retries exhausted — escalate.
        if (prefs != null && prefs.isEscalationEnabled()) {
            UUID target = escalationService.resolveEscalationTarget(
                    job.getUserId(), prefs, job.getHouseId());
            if (target != null) {
                escalationService.record(job.getId(), null, target,
                        EscalationReason.NO_ANSWER_MAX_RETRIES);
                triggerEscalationDispatch(job, target, RecipientRole.MANAGER,
                        EscalationReason.NO_ANSWER_MAX_RETRIES);
                job.setStatus(VoiceCallStatus.ESCALATED);
                log.info("[Webhook] escalated to userId={} after {} attempts", target, attemptNumber);
                return;
            }
        }
        // Nothing more to do — mark FAILED so audit reports count it.
        job.setStatus(VoiceCallStatus.FAILED);
    }

    /**
     * Re-dispatch the original alert to {@code targetUserId} (landlord
     * or manager) using the channel matrix for that role. Reads the
     * denormalised alert context off the original voice_call_jobs row
     * so we don't need to hit DynamoDB.
     *
     * <p>Suffix the alertId with ".escalated" so the audit trail can
     * tell tenant-vs-escalation calls apart.
     */
    private void triggerEscalationDispatch(VoiceCallJob originalJob,
                                             UUID targetUserId,
                                             RecipientRole targetRole,
                                             EscalationReason reason) {
        AlertEventType eventType;
        try {
            eventType = AlertEventType.valueOf(originalJob.getEventType());
        } catch (IllegalArgumentException e) {
            log.warn("[Webhook] unknown event_type={} on jobId={} — escalation aborted",
                    originalJob.getEventType(), originalJob.getId());
            return;
        }

        Double value = originalJob.getAlertValue() == null ? null
                : originalJob.getAlertValue().doubleValue();

        Map<String, Object> escalationVars = new HashMap<>();
        escalationVars.put("escalated_from_user_id", originalJob.getUserId().toString());
        escalationVars.put("original_call_id", originalJob.getId().toString());

        AlertDispatchRequest redispatch = new AlertDispatchRequest(
                targetUserId,
                originalJob.getAlertId() == null
                        ? "esc-" + originalJob.getId()
                        : originalJob.getAlertId() + ".escalated",
                eventType,
                originalJob.getHouseId(),
                originalJob.getAreaId(),
                originalJob.getAreaName(),
                originalJob.getThing(),
                originalJob.getMetric(),
                value,
                originalJob.getAlertUnit(),
                escalationVars
        );

        try {
            var resp = dispatchService.dispatchDirect(redispatch, targetUserId, targetRole, reason);
            log.info("[Webhook] escalation re-dispatch ok target={} role={} reason={} channels={}",
                    targetUserId, targetRole, reason,
                    resp.results().stream().map(r -> r.channel() + "=" + r.status()).toList());
        } catch (Exception e) {
            log.error("[Webhook] escalation re-dispatch failed target={}: {}",
                    targetUserId, e.getMessage(), e);
        }
    }

    private static VoiceCallStatus mapStatus(String providerStatus) {
        return switch (providerStatus) {
            case "ANSWERED", "answered" -> VoiceCallStatus.ANSWERED;
            case "NO_ANSWER", "no_answer", "NOANSWER" -> VoiceCallStatus.NO_ANSWER;
            case "BUSY", "busy" -> VoiceCallStatus.BUSY;
            case "FAILED", "failed", "CANCELLED", "cancelled" -> VoiceCallStatus.FAILED;
            default -> VoiceCallStatus.DIALING;
        };
    }
}
