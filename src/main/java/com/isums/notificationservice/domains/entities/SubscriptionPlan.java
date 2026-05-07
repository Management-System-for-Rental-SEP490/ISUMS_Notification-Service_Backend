package com.isums.notificationservice.domains.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Landlord-editable plan catalogue. Each row defines the price + duration
 * + bundled quotas for one PREMIUM tier (e.g. {@code PREMIUM_1M},
 * {@code PREMIUM_ANNUAL}, custom promo SKUs).
 *
 * <p>Why DAYS for duration? Lets the operator offer trial periods (7,
 * 14 days), short-term promos, and standard month-aligned plans without
 * branching. The activation logic translates {@code duration_days} to
 * an {@code Instant} expiry by adding to {@code now()} or to the
 * existing {@code premium_until} (top-up semantics).
 *
 * <p>{@code name_translations} stores the ISUMS i18n JSON blob (the
 * same shape used by every other translatable column in the monorepo),
 * so the FE can render the same plan in vi / en / ja without an extra
 * lookup.
 */
@Entity
@Table(name = "subscription_plans")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(name = "name_translations", columnDefinition = "text")
    private String nameTranslations;

    @Column(name = "duration_days", nullable = false)
    private Integer durationDays;

    @Column(name = "price_vnd", nullable = false)
    private Integer priceVnd;

    @Column(name = "voice_quota_monthly", nullable = false)
    @Builder.Default
    private Integer voiceQuotaMonthly = 100;

    @Column(name = "sms_quota_monthly", nullable = false)
    @Builder.Default
    private Integer smsQuotaMonthly = 200;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "is_featured", nullable = false)
    @Builder.Default
    private Boolean isFeatured = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;
}
