package com.isums.notificationservice.domains.dtos;

import com.isums.notificationservice.domains.entities.ManagerNotification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {
    private UUID id;
    private String category;
    private String title;
    private String body;
    private String actionUrl;
    private Map<String, String> metadata;
    private boolean isRead;
    private Instant createdAt;

    public static NotificationDto from(ManagerNotification n) {
        return NotificationDto.builder()
                .id(n.getId())
                .category(n.getCategory().name())
                .title(n.getTitle())
                .body(n.getBody())
                .actionUrl(n.getActionUrl())
                .metadata(n.getMetadata())
                .isRead(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}