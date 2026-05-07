package com.isums.notificationservice.services;

import com.isums.notificationservice.domains.entities.NotificationSubscription;
import com.isums.notificationservice.domains.enums.SubscriptionTier;
import com.isums.notificationservice.infrastructures.repositories.NotificationSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Downgrades PREMIUM subscriptions whose {@code premium_until} has
 * passed. Runs nightly — a one-day lag before the user's voice stops
 * working is acceptable, and simpler than tracking precise expiry
 * timestamps through Redis.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PremiumExpirationScheduler {

    private final NotificationSubscriptionRepository subsRepo;
    private final NotificationSubscriptionService subscriptionService;

    @Scheduled(cron = "0 15 2 * * *", zone = "Asia/Ho_Chi_Minh")
    public void sweepExpired() {
        Instant now = Instant.now();
        List<NotificationSubscription> expired =
                subsRepo.findAllByTierAndPremiumUntilBefore(SubscriptionTier.PREMIUM, now);

        if (expired.isEmpty()) return;
        log.info("[PremiumExpire] downgrading {} expired subscriptions", expired.size());

        for (NotificationSubscription sub : expired) {
            try {
                subscriptionService.downgradeToFree(sub.getUserId());
            } catch (Exception e) {
                log.error("[PremiumExpire] failed userId={}: {}",
                        sub.getUserId(), e.getMessage(), e);
            }
        }
    }
}
