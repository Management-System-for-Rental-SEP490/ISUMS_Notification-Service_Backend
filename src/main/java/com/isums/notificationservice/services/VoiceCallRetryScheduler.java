package com.isums.notificationservice.services;

import com.isums.notificationservice.domains.entities.UserNotificationPreferences;
import com.isums.notificationservice.domains.entities.VoiceCallJob;
import com.isums.notificationservice.domains.enums.VoiceCallStatus;
import com.isums.notificationservice.infrastructures.repositories.UserNotificationPreferencesRepository;
import com.isums.notificationservice.infrastructures.repositories.VoiceCallJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Runs every minute, finds voice_call_jobs whose {@code next_retry_at} is
 * past and whose attempt budget still allows another dial, and re-invokes
 * the orchestrator with a fresh attempt counter.
 *
 * <p>Holds no state: safe to run across multiple JVM instances if that ever
 * happens, but JPA row-level locking is not used — a duplicate dial is
 * expensive and rare enough that we accept it as a trade-off. (Can be
 * tightened with a pessimistic lock or Redis lease if needed.)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VoiceCallRetryScheduler {

    private final VoiceCallJobRepository voiceJobRepo;
    private final UserNotificationPreferencesRepository prefsRepo;
    private final VoiceCallOrchestratorService voiceOrchestrator;

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void sweepRetries() {
        Instant now = Instant.now();
        List<VoiceCallJob> candidates = voiceJobRepo.findAllByStatusInAndNextRetryAtBefore(
                List.of(VoiceCallStatus.NO_ANSWER, VoiceCallStatus.BUSY), now);

        if (candidates.isEmpty()) return;

        log.info("[VoiceRetry] sweeping {} candidates", candidates.size());

        for (VoiceCallJob job : candidates) {
            try {
                if (job.getAttemptNumber() >= job.getMaxAttempts()) {
                    job.setStatus(VoiceCallStatus.FAILED);
                    job.setNextRetryAt(null);
                    voiceJobRepo.save(job);
                    continue;
                }

                UserNotificationPreferences prefs =
                        prefsRepo.findById(job.getUserId()).orElse(null);
                if (prefs == null || !prefs.isVoiceEnabled()) {
                    job.setStatus(VoiceCallStatus.SKIPPED);
                    job.setNextRetryAt(null);
                    voiceJobRepo.save(job);
                    continue;
                }

                // New attempt. Clear the retry timer + bump counter so the
                // webhook path can distinguish a fresh dial from a stale one.
                job.setAttemptNumber(job.getAttemptNumber() + 1);
                job.setNextRetryAt(null);
                job.setStatus(VoiceCallStatus.PENDING);
                voiceJobRepo.save(job);

                voiceOrchestrator.dial(job, job.getRenderedText(), job.getLocale(), prefs);
            } catch (Exception e) {
                log.error("[VoiceRetry] failed to retry jobId={}: {}",
                        job.getId(), e.getMessage(), e);
            }
        }
    }
}
