package com.isums.notificationservice.domains.entities;

import com.isums.notificationservice.domains.enums.LocaleType;
import com.isums.notificationservice.domains.enums.VoiceGender;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "user_notification_preferences")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserNotificationPreferences {

    @Id
    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private LocaleType language = LocaleType.vi_VN;

    @Column(name = "email_enabled", nullable = false)
    @Builder.Default
    private boolean emailEnabled = true;

    @Column(name = "push_enabled", nullable = false)
    @Builder.Default
    private boolean pushEnabled = true;

    @Column(name = "sms_enabled", nullable = false)
    @Builder.Default
    private boolean smsEnabled = false;

    @Column(name = "voice_enabled", nullable = false)
    @Builder.Default
    private boolean voiceEnabled = false;

    /**
     * Master switch for the quiet-hours window. When false, alerts ride
     * through any time of day regardless of {@code quietHoursStart}/
     * {@code quietHoursEnd}. Defaults to true so existing behaviour
     * stays the same after migration.
     */
    @Column(name = "quiet_hours_enabled", nullable = false)
    @Builder.Default
    private boolean quietHoursEnabled = true;

    @Column(name = "quiet_hours_start", nullable = false)
    @Builder.Default
    private LocalTime quietHoursStart = LocalTime.of(22, 0);

    @Column(name = "quiet_hours_end", nullable = false)
    @Builder.Default
    private LocalTime quietHoursEnd = LocalTime.of(6, 0);

    @Column(name = "quiet_hours_override_critical", nullable = false)
    @Builder.Default
    private boolean quietHoursOverrideCritical = true;

    // voiceMaxRetries=1 → 2 total dial attempts (1 initial + 1 retry).
    // After both fail to answer, VoiceWebhookHandler.scheduleRetryOrEscalate
    // escalates to MANAGER with reason=NO_ANSWER_MAX_RETRIES.
    @Column(name = "voice_max_retries", nullable = false)
    @Builder.Default
    private int voiceMaxRetries = 1;

    // Short retry interval — emergency alerts shouldn't wait 2 minutes
    // between calls. 60s gives the user enough time to glance at the
    // phone screen without making the system feel slow.
    @Column(name = "voice_retry_interval_sec", nullable = false)
    @Builder.Default
    private int voiceRetryIntervalSec = 60;

    @Column(name = "voice_rate_limit_sec", nullable = false)
    @Builder.Default
    private int voiceRateLimitSec = 300;

    @Enumerated(EnumType.STRING)
    @Column(name = "voice_gender", nullable = false, length = 10)
    @Builder.Default
    private VoiceGender voiceGender = VoiceGender.FEMALE;

    @Column(name = "voice_speed", nullable = false, precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal voiceSpeed = new BigDecimal("1.00");

    @Column(name = "dtmf_ack_enabled", nullable = false)
    @Builder.Default
    private boolean dtmfAckEnabled = true;

    @Column(name = "escalation_enabled", nullable = false)
    @Builder.Default
    private boolean escalationEnabled = true;

    @Column(name = "escalation_target_user_id")
    private UUID escalationTargetUserId;

    @Column(name = "voice_consent_given_at")
    private Instant voiceConsentGivenAt;

    /**
     * Version of the T&C text the user agreed to. When the legal team
     * publishes new wording, prior consents become "stale" and the user
     * is re-prompted on next login. PDPL audit requirement.
     */
    @Column(name = "voice_consent_text_version", length = 20)
    private String voiceConsentTextVersion;

    /**
     * IP that submitted the grant — required by PDPL Điều 11 for
     * non-repudiation. Stored as PostgreSQL {@code inet} via String.
     */
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.INET)
    @Column(name = "voice_consent_ip", columnDefinition = "inet")
    private String voiceConsentIp;

    @Column(name = "voice_consent_user_agent", columnDefinition = "text")
    private String voiceConsentUserAgent;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
