package com.isums.notificationservice.domains.entities;

import com.isums.notificationservice.domains.enums.NotificationCategory;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "manager_notifications",
        indexes = {
                @Index(columnList = "recipient_id, is_read, created_at"),
                @Index(columnList = "recipient_id, category")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ManagerNotification {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationCategory category;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String body;

    private String actionUrl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> metadata;

    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    private Instant readAt;

    @CreationTimestamp
    private Instant createdAt;
}
