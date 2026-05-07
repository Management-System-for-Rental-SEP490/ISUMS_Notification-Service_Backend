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

    @Transactional
    public NotificationSubscription activatePremium(UUID userId, UUID houseId, int months) {
        return activatePremiumByDays(userId, houseId, Math.max(1, months) * 30);
    }

    @Transactional
    public NotificationSubscription activatePremiumByDays(UUID userId, UUID houseId, int durationDays) {
        return activatePremiumByDays(userId, houseId, durationDays,
                TierQuotaPolicy.voiceQuotaFor(SubscriptionTier.PREMIUM),
                TierQuotaPolicy.smsQuotaFor(SubscriptionTier.PREMIUM));
    }

    @Transactional
    public NotificationSubscription activatePremiumByDays(UUID userId, UUID houseId, int durationDays,
                                                          int voiceQuotaMonthly,
                                                          int smsQuotaMonthly) {
        if (houseId == null) {
            throw new IllegalArgumentException("houseId is required for per-house PREMIUM activation");
        }
        int days = Math.max(1, durationDays);
        int voiceQuota = Math.max(voiceQuotaMonthly,
                TierQuotaPolicy.voiceQuotaFor(SubscriptionTier.PREMIUM));
        int smsQuota = Math.max(smsQuotaMonthly,
                TierQuotaPolicy.smsQuotaFor(SubscriptionTier.PREMIUM));

        NotificationSubscription sub = subsRepo.findByUserIdAndHouseId(userId, houseId)
                .orElseGet(() -> NotificationSubscription.builder()
                        .userId(userId)
                        .houseId(houseId)
                        .build());

        Instant now = Instant.now();
        Instant newUntil;
        if (sub.getTier() == SubscriptionTier.PREMIUM
                && sub.getPremiumUntil() != null
                && sub.getPremiumUntil().isAfter(now)) {
            newUntil = sub.getPremiumUntil().plus(days, ChronoUnit.DAYS);
        } else {
            sub.setPremiumStartedAt(now);
            newUntil = now.plus(days, ChronoUnit.DAYS);
        }

        sub.setTier(SubscriptionTier.PREMIUM);
        sub.setPremiumUntil(newUntil);
        sub.setVoiceQuotaMonthly(voiceQuota);
        sub.setSmsQuotaMonthly(smsQuota);

        NotificationSubscription saved = subsRepo.save(sub);
        log.info("[Subscription] PREMIUM activated userId={} houseId={} days={} until={} voiceQuota={} smsQuota={}",
                userId, houseId, days, newUntil, saved.getVoiceQuotaMonthly(), saved.getSmsQuotaMonthly());
        return saved;
    }

    @Transactional
    public NotificationSubscription downgradeToFree(UUID userId, UUID houseId) {
        NotificationSubscription sub = subsRepo.findByUserIdAndHouseId(userId, houseId).orElse(null);
        if (sub == null) return null;
        sub.setTier(SubscriptionTier.FREE);
        sub.setPremiumUntil(null);
        sub.setVoiceQuotaMonthly(TierQuotaPolicy.voiceQuotaFor(SubscriptionTier.FREE));
        sub.setSmsQuotaMonthly(TierQuotaPolicy.smsQuotaFor(SubscriptionTier.FREE));
        NotificationSubscription saved = subsRepo.save(sub);
        log.info("[Subscription] downgraded userId={} houseId={}", userId, houseId);
        return saved;
    }

    public SubscriptionDto toDto(NotificationSubscription s) {
        int voiceRemaining = Math.max(0, s.getVoiceQuotaMonthly() - s.getVoiceUsedThisMonth());
        int smsRemaining   = Math.max(0, s.getSmsQuotaMonthly()   - s.getSmsUsedThisMonth());
        return new SubscriptionDto(
                s.getUserId(), s.getHouseId(), s.getTier(),
                s.getPremiumStartedAt(), s.getPremiumUntil(),
                s.getVoiceQuotaMonthly(), s.getVoiceUsedThisMonth(), voiceRemaining,
                s.getSmsQuotaMonthly(),   s.getSmsUsedThisMonth(),   smsRemaining,
                s.getQuotaResetAt()
        );
    }
}
