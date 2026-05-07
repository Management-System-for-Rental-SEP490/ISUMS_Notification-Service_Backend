package com.isums.notificationservice.infrastructures.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.isums.common.i18n.TranslationMap;
import com.isums.common.i18n.events.TextTranslationResultEvent;
import com.isums.notificationservice.domains.entities.ManagerNotification;
import com.isums.notificationservice.infrastructures.Websockets.SseConnectionManager;
import com.isums.notificationservice.infrastructures.repositories.ManagerNotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Consumes {@link TextTranslationResultEvent} from AI-Service and merges the
 * translated text into {@code titleTranslations} / {@code bodyTranslations}
 * for the matching {@link ManagerNotification}. Results arrive one per target
 * locale; we never overwrite a value that's already present (preserves manual
 * edits via {@link TranslationMap#mergeAutoFilled}).
 *
 * <p>FAILED results are logged and dropped; the FE shows them as missing and
 * the user can manually translate via the sync endpoint.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationTranslationResultListener {

    private final ObjectMapper objectMapper;
    private final ManagerNotificationRepository repository;
    private final SseConnectionManager sseManager;

    @KafkaListener(topics = NotificationTranslationRequester.CALLBACK_TOPIC,
            groupId = "notification-translation-result")
    @Transactional
    public void onResult(String payload, Acknowledgment ack) {
        try {
            TextTranslationResultEvent event = objectMapper.readValue(payload, TextTranslationResultEvent.class);
            if (!TextTranslationResultEvent.STATUS_DONE.equals(event.status())
                    || event.translatedText() == null
                    || event.translatedText().isBlank()) {
                log.debug("Skipping non-DONE translation result requestId={} status={}",
                        event.requestId(), event.status());
                ack.acknowledge();
                return;
            }
            apply(event);
            ack.acknowledge();
        } catch (Exception ex) {
            log.error("Failed to apply translation result, payload={}", payload, ex);
            ack.acknowledge(); // do not retry on parse errors
        }
    }

    private void apply(TextTranslationResultEvent event) {
        UUID id = event.resourceId();
        Optional<ManagerNotification> opt = repository.findById(id);
        if (opt.isEmpty()) {
            log.debug("Notification {} not found for translation result; likely deleted", id);
            return;
        }
        ManagerNotification n = opt.get();
        Map<String, String> patch = new LinkedHashMap<>();
        patch.put(event.targetLanguage(), event.translatedText());

        boolean isTitle = "notification.title".equals(event.resourceType());
        boolean isBody = "notification.body".equals(event.resourceType());

        if (isTitle) {
            TranslationMap before = n.getTitleTranslations() == null ? TranslationMap.empty() : n.getTitleTranslations();
            n.setTitleTranslations(before.mergeAutoFilled(patch));
        } else if (isBody) {
            TranslationMap before = n.getBodyTranslations() == null ? TranslationMap.empty() : n.getBodyTranslations();
            n.setBodyTranslations(before.mergeAutoFilled(patch));
        } else {
            log.warn("Unknown resourceType for notification translation: {}", event.resourceType());
            return;
        }
        ManagerNotification persisted = repository.save(n);
        log.debug("Applied translation resourceType={} resourceId={} target={}",
                event.resourceType(), id, event.targetLanguage());

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sseManager.push(persisted.getRecipientId(), persisted);
                }
            });
        } else {
            sseManager.push(persisted.getRecipientId(), persisted);
        }
    }
}
