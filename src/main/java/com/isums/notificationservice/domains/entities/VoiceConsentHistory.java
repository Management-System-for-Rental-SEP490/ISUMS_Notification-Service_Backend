package com.isums.notificationservice.domains.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit row — one entry per voice consent state transition.
 *
 * <p>Mandated by PDPL (Nghị định 13/2023/NĐ-CP, Điều 11–13) and Thông
 * tư 22/2021/TT-BTTTT: every grant or revoke must be reproducible from
 * audit data, including the exact text version the user agreed to, the
 * IP address they submitted from, and their user agent.
 *
 * <p>Retention: 5 years (telecom compliance window). Rows are NEVER
 * deleted — admin downgrades or auto-expiry produce a new row with
 * action=REVOKED|EXPIRED instead.
 */
@Entity
@Table(name = "voice_consent_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VoiceConsentHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Action action;

    @Column(name = "text_version", length = 20)
    private String textVersion;

    /** PostgreSQL inet — store as string in JPA for portability. */
    @JdbcTypeCode(SqlTypes.INET)
    @Column(columnDefinition = "inet")
    private String ip;

    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;

    @Enumerated(EnumType.STRING)
    @Column(name = "initiated_by", nullable = false, length = 20)
    @Builder.Default
    private InitiatedBy initiatedBy = InitiatedBy.USER;

    @Column(name = "initiated_by_id")
    private UUID initiatedById;

    @Column(columnDefinition = "text")
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public enum Action { GRANTED, REVOKED, EXPIRED }
    public enum InitiatedBy { USER, ADMIN, SYSTEM }
}
