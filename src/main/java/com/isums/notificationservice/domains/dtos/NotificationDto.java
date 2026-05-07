package com.isums.notificationservice.domains.dtos;

import com.isums.common.i18n.TranslationMap;
import com.isums.notificationservice.domains.entities.ManagerNotification;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Notification DTO. Carries both source-language text ({@code title}/{@code body})
 * and the per-locale translation maps so the FE can either display the active
 * locale ({@code resolveTitle()}) or render an editor with all locales visible.
 *
 * <p>{@link #from(ManagerNotification)} is the unresolved factory — it copies
 * everything and lets callers (or the FE) decide which locale to show.
 * {@link #from(ManagerNotification, String)} resolves once on the server side
 * for callers that just want a flat string.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {
    private UUID id;
    private String category;
    private String title;
    private Map<String, String> titleTranslations;
    private String body;
    private Map<String, String> bodyTranslations;
    private String actionUrl;
    private Map<String, String> metadata;
    private boolean isRead;
    private Instant createdAt;

    public static NotificationDto from(ManagerNotification n) {
        return NotificationDto.builder()
                .id(n.getId())
                .category(n.getCategory().name())
                .title(n.getTitle())
                .titleTranslations(n.getTitleTranslations() == null ? null : n.getTitleTranslations().asMap())
                .body(n.getBody())
                .bodyTranslations(n.getBodyTranslations() == null ? null : n.getBodyTranslations().asMap())
                .actionUrl(n.getActionUrl())
                .metadata(n.getMetadata())
                .isRead(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }

    public static NotificationDto from(ManagerNotification n, String preferredLocale) {
        NotificationDto dto = from(n);
        if (preferredLocale != null) {
            TranslationMap titleMap = n.getTitleTranslations();
            if (titleMap != null) {
                String resolved = titleMap.resolve(preferredLocale);
                if (resolved != null && !resolved.isBlank()) dto.setTitle(resolved);
            }
            TranslationMap bodyMap = n.getBodyTranslations();
            if (bodyMap != null) {
                String resolved = bodyMap.resolve(preferredLocale);
                if (resolved != null && !resolved.isBlank()) dto.setBody(resolved);
            }
        }
        return dto;
    }
}
