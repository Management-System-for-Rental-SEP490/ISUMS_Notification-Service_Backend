package com.isums.notificationservice.services;

import com.isums.notificationservice.domains.dtos.NotificationDto;
import com.isums.notificationservice.domains.entities.ManagerNotification;
import com.isums.notificationservice.domains.enums.NotificationCategory;
import com.isums.notificationservice.exceptions.NotFoundException;
import com.isums.notificationservice.infrastructures.Websockets.SseConnectionManager;
import com.isums.notificationservice.infrastructures.abstracts.ManagerNotificationService;
import com.isums.notificationservice.infrastructures.repositories.ManagerNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ManagerNotificationServiceImpl implements ManagerNotificationService {

    private final ManagerNotificationRepository repo;
    private final SseConnectionManager sseManager;

    @Override
    @Transactional
    public void send(UUID recipientId, NotificationCategory category,
                     String title, String body,
                     String actionUrl, Map<String, String> metadata) {

        ManagerNotification n = ManagerNotification.builder()
                .recipientId(recipientId)
                .category(category)
                .title(title)
                .body(body)
                .actionUrl(actionUrl)
                .metadata(metadata)
                .isRead(false)
                .build();

        repo.save(n);
        sseManager.push(recipientId, n);

        log.info("[Notification] Sent recipientId={} category={}", recipientId, category);
    }

    @Override
    public Page<NotificationDto> getByRecipient(UUID recipientId, Pageable pageable) {
        return repo.findByRecipientIdOrderByCreatedAtDesc(recipientId, pageable)
                .map(NotificationDto::from);
    }

    @Override
    public long countUnread(UUID recipientId) {
        return repo.countByRecipientIdAndIsReadFalse(recipientId);
    }

    @Override
    @Transactional
    public void markRead(UUID notificationId, UUID recipientId) {
        ManagerNotification n = repo.findByIdAndRecipientId(notificationId, recipientId)
                .orElseThrow(() -> new NotFoundException("Notification not found"));
        n.setRead(true);
        n.setReadAt(Instant.now());
        repo.save(n);
    }

    @Override
    @Transactional
    public void markAllRead(UUID recipientId) {
        repo.markAllReadByRecipientId(recipientId, Instant.now());
    }
}
