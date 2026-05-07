package com.isums.notificationservice.services;

import com.isums.notificationservice.domains.dtos.SubscriptionDto;
import com.isums.notificationservice.domains.entities.NotificationSubscription;
import com.isums.notificationservice.domains.enums.SubscriptionTier;
import com.isums.notificationservice.infrastructures.repositories.NotificationSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationSubscriptionService {

    private final NotificationSubscriptionRepository subsRepo;

    /**
     * Month-based PREMIUM grant — kept for the admin "grant-premium" demo
     * endpoint where months are the natural unit. Production payment flow
     * goes through {@link #activatePremiumByDays} so a 7-day trial buys
     * 7 days, not a rounded-up month.
     */
    @Transactional
    public NotificationSubscription activatePremium(UUID userId, int months) {
        return activatePremiumByDays(userId, Math.max(1, months) * 30);
    }

    /**
     * Plan-driven PREMIUM grant. {@code durationDays} comes straight from
     * the subscription_plans row that was paid for, so a 7-day trial gets
     * 7 days and an annual plan gets 365 — no monthly rounding error.
     *
     * <p>Quotas default to {@link TierQuotaPolicy} (the legacy 20/30
     * floor) — callers with a curated plan should use the overload that
     * takes plan quotas so a "Pro 1M" plan with 100 voice / 200 SMS
     * doesn't get downgraded to the legacy ceiling on activation.
     */
    @Transactional
    public NotificationSubscription activatePremiumByDays(UUID userId, int durationDays) {
        return activatePremiumByDays(userId, durationDays,
                TierQuotaPolicy.voiceQuotaFor(SubscriptionTier.PREMIUM),
                TierQuotaPolicy.smsQuotaFor(SubscriptionTier.PREMIUM));
    }

    /**
     * Plan-driven PREMIUM grant with explicit quotas. Pass the values from
     * {@code subscription_plans.voice_quota_monthly / sms_quota_monthly}
     * so the user's monthly cap matches what they paid for.
     *
     * <p>Idempotent on top of itself: a Kafka redelivery hitting this with
     * the same userId stacks days onto the existing premium_until (the
     * "user paid twice, gets twice the time" semantics matches the legacy
     * months path). Caller must guard against same-event redelivery via
     * {@code IdempotencyService#isDuplicate}.
     */
    @Transactional
    public NotificationSubscription activatePremiumByDays(UUID userId, int durationDays,
                                                          int voiceQuotaMonthly,
                                                          int smsQuotaMonthly) {
        int days = Math.max(1, durationDays);
        // Floor at the tier policy minimum so a misconfigured plan can never
        // shrink the user below the baseline they expect from PREMIUM —
        // upper bound stays at whatever the plan says.
        int voiceQuota = Math.max(voiceQuotaMonthly,
                TierQuotaPolicy.voiceQuotaFor(SubscriptionTier.PREMIUM));
        int smsQuota = Math.max(smsQuotaMonthly,
                TierQuotaPolicy.smsQuotaFor(SubscriptionTier.PREMIUM));

        NotificationSubscription sub = subsRepo.findById(userId)
                .orElseGet(() -> NotificationSubscription.builder().userId(userId).build());

        Instant now = Instant.now();
        Instant newUntil;
        if (sub.getTier() == SubscriptionTier.PREMIUM
                && sub.getPremiumUntil() != null
                && sub.getPremiumUntil().isAfter(now)) {
            // Extend from existing end-date, not from now — user pays to stack.
            newUntil = sub.getPremiumUntil().plus(days, ChronoUnit.DAYS);
        } else {
            sub.setPremiumStartedAt(now);
            newUntil = now.plus(days, ChronoUnit.DAYS);
        }

        sub.setTier(SubscriptionTier.PREMIUM);
        sub.setPremiumUntil(newUntil);
        // Plan quotas reset on activation (and on every renewal/extension)
        // so a user upgrading from a smaller plan inherits the new quota
        // immediately. Used-counters intentionally stay so we don't "free
        // refill" by spamming activations within the same month.
        sub.setVoiceQuotaMonthly(voiceQuota);
        sub.setSmsQuotaMonthly(smsQuota);

        NotificationSubscription saved = subsRepo.save(sub);
        log.info("[Subscription] PREMIUM activated userId={} days={} until={} voiceQuota={} smsQuota={}",
                userId, days, newUntil, saved.getVoiceQuotaMonthly(), saved.getSmsQuotaMonthly());
        return saved;
    }

    @Transactional
    public NotificationSubscription downgradeToFree(UUID userId) {
        NotificationSubscription sub = subsRepo.findById(userId).orElse(null);
        if (sub == null) return null;
        sub.setTier(SubscriptionTier.FREE);
        sub.setPremiumUntil(null);
        sub.setVoiceQuotaMonthly(TierQuotaPolicy.voiceQuotaFor(SubscriptionTier.FREE));
        sub.setSmsQuotaMonthly(TierQuotaPolicy.smsQuotaFor(SubscriptionTier.FREE));
        NotificationSubscription saved = subsRepo.save(sub);
        log.info("[Subscription] downgraded userId={}", userId);
        return saved;
    }

    public SubscriptionDto toDto(NotificationSubscription s) {
        int voiceRemaining = Math.max(0, s.getVoiceQuotaMonthly() - s.getVoiceUsedThisMonth());
        int smsRemaining   = Math.max(0, s.getSmsQuotaMonthly()   - s.getSmsUsedThisMonth());
        return new SubscriptionDto(
                s.getUserId(), s.getTier(),
                s.getPremiumStartedAt(), s.getPremiumUntil(),
                s.getVoiceQuotaMonthly(), s.getVoiceUsedThisMonth(), voiceRemaining,
                s.getSmsQuotaMonthly(),   s.getSmsUsedThisMonth(),   smsRemaining,
                s.getQuotaResetAt()
        );
    }
}
