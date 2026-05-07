package com.isums.notificationservice.domains.entities;

import com.isums.notificationservice.domains.enums.EscalationReason;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "voice_call_escalations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceCallEscalation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "original_call_id", nullable = false, columnDefinition = "uuid")
    private UUID originalCallId;

    @Column(name = "escalated_call_id", columnDefinition = "uuid")
    private UUID escalatedCallId;

    @Column(name = "escalated_to_user_id", nullable = false, columnDefinition = "uuid")
    private UUID escalatedToUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private EscalationReason reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
