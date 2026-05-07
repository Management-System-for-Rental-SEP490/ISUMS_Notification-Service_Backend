package com.isums.notificationservice.infrastructures.repositories;

import com.isums.notificationservice.domains.entities.NotificationSubscription;
import com.isums.notificationservice.domains.enums.SubscriptionTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationSubscriptionRepository
        extends JpaRepository<NotificationSubscription, UUID> {

    List<NotificationSubscription> findAllByTierAndPremiumUntilBefore(
            SubscriptionTier tier, Instant cutoff);
}
