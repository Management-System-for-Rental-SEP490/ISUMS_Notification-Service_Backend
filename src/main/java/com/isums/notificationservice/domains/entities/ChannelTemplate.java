package com.isums.notificationservice.domains.entities;

import com.isums.notificationservice.domains.enums.NotificationChannel;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "channel_templates",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_channel_tpl_key_channel",
                columnNames = {"template_key", "channel"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChannelTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "template_key", nullable = false, length = 100)
    private String templateKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel;

    @Column(name = "event_type", length = 80)
    private String eventType;

    @Column(length = 50)
    private String category;

    @Column(name = "recipient_type", length = 50)
    private String recipientType;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "updated_by", length = 100)
    private String updatedBy;
}
