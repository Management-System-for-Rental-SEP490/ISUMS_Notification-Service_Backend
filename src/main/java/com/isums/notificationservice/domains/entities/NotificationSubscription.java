package com.isums.notificationservice.domains.entities;

import com.isums.notificationservice.domains.enums.SubscriptionTier;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@IdClass(NotificationSubscriptionId.class)
public class NotificationSubscription {

    @Id
    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @Id
    @Column(name = "house_id", columnDefinition = "uuid")
    private UUID houseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SubscriptionTier tier = SubscriptionTier.FREE;

    @Column(name = "premium_started_at")
    private Instant premiumStartedAt;

    @Column(name = "premium_until")
    private Instant premiumUntil;

    @Column(name = "voice_quota_monthly", nullable = false)
    @Builder.Default
    private int voiceQuotaMonthly = 0;

    @Column(name = "voice_used_this_month", nullable = false)
    @Builder.Default
    private int voiceUsedThisMonth = 0;

    @Column(name = "sms_quota_monthly", nullable = false)
    @Builder.Default
    private int smsQuotaMonthly = 0;

    @Column(name = "sms_used_this_month", nullable = false)
    @Builder.Default
    private int smsUsedThisMonth = 0;

    @Column(name = "quota_reset_at", nullable = false)
    @Builder.Default
    private Instant quotaResetAt = Instant.now();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
