package com.isums.notificationservice.domains.entities;

import com.isums.notificationservice.domains.enums.LocaleType;
import com.isums.notificationservice.domains.enums.VoiceCallStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "voice_call_jobs", indexes = {
        @Index(name = "ix_voice_job_user_created", columnList = "user_id, created_at DESC"),
        @Index(name = "ix_voice_job_status_retry", columnList = "status, next_retry_at"),
        @Index(name = "ix_voice_job_provider_call", columnList = "provider_call_id"),
        @Index(name = "ix_voice_job_alert", columnList = "alert_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceCallJob {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(name = "alert_id", length = 100)
    private String alertId;

    @Column(name = "event_type", nullable = false, length = 80)
    private String eventType;

    @Column(nullable = false, length = 40)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LocaleType locale;

    @Column(name = "template_id", columnDefinition = "uuid")
    private UUID templateId;

    @Column(name = "template_version_id", columnDefinition = "uuid")
    private UUID templateVersionId;

    @Column(name = "rendered_text", nullable = false, columnDefinition = "text")
    private String renderedText;

    // Denormalised alert context — used by the webhook handler to
    // re-dispatch the same alert to landlord / manager on DTMF=2 or
    // NO_ANSWER_MAX_RETRIES without re-querying DynamoDB.
    @Column(name = "house_id", length = 64)
    private String houseId;

    @Column(name = "area_id", length = 64)
    private String areaId;

    @Column(name = "area_name", length = 200)
    private String areaName;

    @Column(length = 100)
    private String thing;

    @Column(length = 40)
    private String metric;

    @Column(name = "alert_value", precision = 10, scale = 2)
    private java.math.BigDecimal alertValue;

    @Column(name = "alert_unit", length = 20)
    private String alertUnit;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String provider = "SPEEDSMS";

    @Column(name = "provider_call_id", length = 100)
    private String providerCallId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private VoiceCallStatus status = VoiceCallStatus.PENDING;

    @Column(name = "dtmf_received", length = 10)
    private String dtmfReceived;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "attempt_number", nullable = false)
    @Builder.Default
    private int attemptNumber = 1;

    @Column(name = "max_attempts", nullable = false)
    @Builder.Default
    private int maxAttempts = 3;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "duration_sec")
    private Integer durationSec;

    @Column(name = "cost_vnd")
    private Integer costVnd;

    @Column(name = "recording_url", columnDefinition = "text")
    private String recordingUrl;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
