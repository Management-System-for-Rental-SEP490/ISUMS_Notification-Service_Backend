package com.isums.notificationservice.infrastructures.kafka;

import com.isums.common.i18n.SupportedLocales;
import com.isums.common.i18n.TranslationMap;
import com.isums.common.i18n.events.TextTranslationRequestedEvent;
import com.isums.common.i18n.events.TranslationIntent;
import com.isums.notificationservice.domains.entities.ManagerNotification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Publishes {@link TextTranslationRequestedEvent} for each translatable field
 * on a notification that is missing locales. Caller (the service layer) invokes
 * {@link #requestMissing(ManagerNotification, String)} after persisting.
 *
 * <p>Disabled via {@code isums.i18n.notification.auto-translate=false} for
 * rollback. {@code isums.i18n.notification.required-locales} controls which
 * locales we expect every notification to have (default: vi,en,ja).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationTranslationRequester {

    static final String CALLBACK_TOPIC = "text.translation.result.notification";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${isums.i18n.notification.auto-translate:true}")
    private boolean autoTranslate;

    @Value("${isums.i18n.notification.required-locales:vi,en,ja}")
    private String requiredLocalesCsv;

    @Value("${isums.i18n.notification.default-source:en}")
    private String defaultSourceLanguage;

    public void requestMissing(ManagerNotification n, String sourceLanguageOverride) {
        if (!autoTranslate || n == null || n.getId() == null) return;
        Set<String> required = parseLocales();
        String source = sourceLanguageOverride != null ? sourceLanguageOverride : defaultSourceLanguage;

        if (n.getTitle() != null && !n.getTitle().isBlank()) {
            List<String> missing = computeMissing(n.getTitleTranslations(), required, source);
            if (!missing.isEmpty()) {
                publish(n.getId(), "notification.title", "title", n.getTitle(), source, missing);
            }
        }
        if (n.getBody() != null && !n.getBody().isBlank()) {
            List<String> missing = computeMissing(n.getBodyTranslations(), required, source);
            if (!missing.isEmpty()) {
                publish(n.getId(), "notification.body", "body", n.getBody(), source, missing);
            }
        }
    }

    private Set<String> parseLocales() {
        Set<String> out = new java.util.LinkedHashSet<>();
        for (String raw : requiredLocalesCsv.split(",")) {
            String code = TranslationMap.normalizeLanguage(raw);
            if (code != null && SupportedLocales.isSupported(code)) out.add(code);
        }
        if (out.isEmpty()) out.addAll(SupportedLocales.ALL);
        return out;
    }

    private List<String> computeMissing(TranslationMap existing, Set<String> required, String source) {
        Set<String> have = existing == null ? Set.of() : existing.languagesPresent();
        List<String> missing = new ArrayList<>();
        for (String locale : required) {
            // Source already exists in main column, no need to translate to itself
            if (locale.equals(source)) continue;
            if (!have.contains(locale)) missing.add(locale);
        }
        return missing;
    }

    private void publish(UUID resourceId, String resourceType, String fieldName,
                         String text, String source, List<String> targets) {
        TextTranslationRequestedEvent event = new TextTranslationRequestedEvent(
                UUID.randomUUID(),
                resourceType,
                resourceId,
                fieldName,
                text,
                source,
                targets,
                TranslationIntent.CUSTOMER_FACING_UI,
                Boolean.TRUE,
                Instant.now(),
                CALLBACK_TOPIC);
        try {
            kafkaTemplate.send(TextTranslationRequestedEvent.TOPIC, resourceId.toString(), event);
            log.debug("Requested translation resourceType={} resourceId={} targets={}",
                    resourceType, resourceId, targets);
        } catch (Exception ex) {
            log.warn("Failed to publish translation request resourceType={} resourceId={}: {}",
                    resourceType, resourceId, ex.toString());
        }
    }
}
