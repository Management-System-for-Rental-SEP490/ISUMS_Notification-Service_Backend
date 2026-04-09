package com.isums.notificationservice.infrastructures.abstracts;

import com.isums.notificationservice.domains.dtos.NotificationDto;
import com.isums.notificationservice.domains.enums.NotificationCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.UUID;

public interface ManagerNotificationService {

    void send(UUID recipientId, NotificationCategory category,
              String title, String body,
              String actionUrl, Map<String, String> metadata);

    Page<NotificationDto> getByRecipient(UUID recipientId, Pageable pageable);

    long countUnread(UUID recipientId);

    void markRead(UUID notificationId, UUID recipientId);

    void markAllRead(UUID recipientId);
}
