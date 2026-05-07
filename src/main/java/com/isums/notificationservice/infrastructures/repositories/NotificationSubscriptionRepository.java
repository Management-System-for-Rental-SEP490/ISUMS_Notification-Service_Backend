package com.isums.notificationservice.infrastructures.repositories;

import com.isums.notificationservice.domains.entities.NotificationSubscription;
import com.isums.notificationservice.domains.entities.NotificationSubscriptionId;
import com.isums.notificationservice.domains.enums.SubscriptionTier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationSubscriptionRepository
        extends JpaRepository<NotificationSubscription, NotificationSubscriptionId> {

    Optional<NotificationSubscription> findByUserIdAndHouseId(UUID userId, UUID houseId);

    List<NotificationSubscription> findAllByUserId(UUID userId);

    List<NotificationSubscription> findAllByTierAndPremiumUntilBefore(
            SubscriptionTier tier, Instant cutoff);
}
