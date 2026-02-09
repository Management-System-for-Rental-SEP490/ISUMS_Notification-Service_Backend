package com.isums.notificationservice.domains.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "EmailTemplates",
        uniqueConstraints = @UniqueConstraint(name = "uq_email_templateKey", columnNames = "templateKey"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String templateKey; // "welcome", "otp"

    @Column(length = 50)
    private String category;

    @Column(length = 50)
    private String recipientType;

    @Column(length = 320)
    private String defaultFromEmail;

    @Column(length = 200)
    private String defaultFromName;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    @Column(length = 100)
    private String createdBy;

    @Column(length = 100)
    private String updatedBy;
}