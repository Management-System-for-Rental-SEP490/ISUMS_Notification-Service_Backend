package com.isums.notificationservice.services;

import com.isums.common.i18n.TranslationMap;
import com.isums.notificationservice.domains.dtos.NotificationDto;
import com.isums.notificationservice.domains.entities.ManagerNotification;
import com.isums.notificationservice.domains.enums.NotificationCategory;
import com.isums.notificationservice.exceptions.NotFoundException;
import com.isums.notificationservice.infrastructures.Websockets.SseConnectionManager;
import com.isums.notificationservice.infrastructures.abstracts.ManagerNotificationService;
import com.isums.notificationservice.infrastructures.kafka.NotificationTranslationRequester;
import com.isums.notificationservice.infrastructures.repositories.ManagerNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ManagerNotificationServiceImpl implements ManagerNotificationService {

    private final ManagerNotificationRepository repo;
    private final SseConnectionManager sseManager;
    private final NotificationTranslationRequester translationRequester;

    @Value("${isums.i18n.notification.source-language:en}")
    private String sourceLanguage = "en";

    @Override
    public void send(UUID recipientId, NotificationCategory category,
                     String title, String body,
                     String actionUrl, Map<String, String> metadata) {
        send(recipientId, category, title, body, sourceLanguage, actionUrl, metadata);
    }

    @Override
    @Transactional
    public void send(UUID recipientId, NotificationCategory category,
                     String title, String body, String sourceLang,
                     String actionUrl, Map<String, String> metadata) {

        String resolvedLang = TranslationMap.normalizeLanguage(sourceLang);
        if (resolvedLang == null || resolvedLang.isBlank()) {
            resolvedLang = TranslationMap.normalizeLanguage(sourceLanguage);
        }
        if (resolvedLang == null || resolvedLang.isBlank()) resolvedLang = "en";

        ManagerNotification n = ManagerNotification.builder()
                .recipientId(recipientId)
                .category(category)
                .title(title)
                .titleTranslations(sourceMap(title, resolvedLang))
                .body(body)
                .bodyTranslations(sourceMap(body, resolvedLang))
                .actionUrl(actionUrl)
                .metadata(metadata)
                .isRead(false)
                .build();

        repo.save(n);
        sseManager.push(recipientId, n);
        translationRequester.requestMissing(n, resolvedLang);

        log.info("[Notification] Sent recipientId={} category={} sourceLang={}",
                recipientId, category, resolvedLang);
    }

    private TranslationMap sourceMap(String text, String lang) {
        if (text == null || text.isBlank()) return TranslationMap.empty();
        Map<String, String> source = new LinkedHashMap<>();
        source.put(lang, text);
        source.put("_source", lang);
        return new TranslationMap(source);
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
