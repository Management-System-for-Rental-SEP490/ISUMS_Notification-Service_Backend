package com.isums.notificationservice.infrastructures.listeners;

import com.isums.notificationservice.domains.dtos.AlertDispatchRequest;
import com.isums.notificationservice.domains.dtos.AlertDispatchResponse;
import com.isums.notificationservice.domains.events.UtilityThresholdExceededEvent;
import com.isums.notificationservice.domains.enums.AlertEventType;
import com.isums.notificationservice.domains.enums.LocaleType;
import com.isums.notificationservice.infrastructures.abstracts.EmailService;
import com.isums.notificationservice.infrastructures.grpcs.UserGrpcClient;
import com.isums.notificationservice.services.NotificationDispatchService;
import com.isums.userservice.grpc.UserResponse;
import common.kafkas.IdempotencyService;
import common.kafkas.KafkaListenerHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class UtilityAlertEventListener {

    private final EmailService emailService;
    private final UserGrpcClient userGrpcClient;
    private final IdempotencyService idempotencyService;
    private final KafkaListenerHelper kafkaHelper;
    private final ObjectMapper objectMapper;
    private final NotificationDispatchService dispatchService;

    private static final DateTimeFormatter DMY = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    @KafkaListener(topics = "utility.consumption.alert", groupId = "notification-group")
    public void onThresholdExceeded(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String messageId = kafkaHelper.extractMessageId(record);
        kafkaHelper.setupMDC(record, messageId);

        try {
            if (idempotencyService.isDuplicate(messageId)) {
                log.warn("[UtilityAlert] duplicate skipped messageId={}", messageId);
                ack.acknowledge();
                return;
            }

            UtilityThresholdExceededEvent event =
                    objectMapper.readValue(record.value(), UtilityThresholdExceededEvent.class);

            if (hasText(event.getTenantUserId())) {
                dispatchTenantAlert(event, event.getTenantUserId());
            } else {
                // Older asset-service builds put the current renter in
                // landlordUserId. Keep that path alive for in-flight Kafka
                // records, then let the new tenantUserId field take over.
                log.warn("[UtilityAlert] tenantUserId missing eventId={}, falling back to legacy landlordUserId",
                        event.getEventId());
                dispatchTenantAlert(event, event.getLandlordUserId());
                sendLegacyEmail(event);
            }

            idempotencyService.markProcessed(messageId);
            ack.acknowledge();

            log.info("[UtilityAlert] processed messageId={} house={} metric={} {}→{} tenantUserId={}",
                    messageId, event.getHouseName(), event.getMetric(),
                    event.getPreviousStatus(), event.getCurrentStatus(), event.getTenantUserId());

        } catch (JacksonException e) {
            log.error("[UtilityAlert] deserialize failed messageId={}: {}", messageId, e.getMessage());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[UtilityAlert] processing failed messageId={}, will retry: {}",
                    messageId, e.getMessage(), e);
            throw new RuntimeException(e);
        } finally {
            kafkaHelper.clearMDC();
        }
    }

    private void dispatchTenantAlert(UtilityThresholdExceededEvent event, String tenantUserId) {
        if (!hasText(tenantUserId)) {
            log.warn("[UtilityAlert] no tenant user id eventId={} houseId={}",
                    event.getEventId(), event.getHouseId());
            return;
        }

        UserResponse tenant;
        try {
            tenant = userGrpcClient.getUserById(UUID.fromString(tenantUserId));
        } catch (Exception e) {
            log.error("[UtilityAlert] tenant lookup failed userId={} eventId={}: {}",
                    tenantUserId, event.getEventId(), e.getMessage());
            return;
        }
        if (tenant == null || !hasText(tenant.getKeycloakId())) {
            log.warn("[UtilityAlert] tenant has no keycloakId userId={} eventId={}",
                    tenantUserId, event.getEventId());
            return;
        }

        UUID tenantKeycloakId;
        try {
            tenantKeycloakId = UUID.fromString(tenant.getKeycloakId());
        } catch (Exception e) {
            log.error("[UtilityAlert] bad tenant keycloakId={} userId={} eventId={}",
                    tenant.getKeycloakId(), tenantUserId, event.getEventId());
            return;
        }

        AlertEventType eventType = mapEventType(event);
        AlertDispatchRequest req = new AlertDispatchRequest(
                tenantKeycloakId,
                safe(event.getEventId(), "utility-" + UUID.randomUUID()),
                eventType,
                event.getHouseId(),
                null,
                safe(event.getHouseName(), "Utility"),
                "utility-threshold",
                safe(event.getMetric(), "utility").toLowerCase(Locale.ROOT),
                event.getUsagePercent(),
                "%",
                dispatchVars(event)
        );

        AlertDispatchResponse resp = dispatchService.dispatch(req);
        log.info("[UtilityAlert] dispatched eventId={} tenantUserId={} tenantKeycloakId={} eventType={} results={}",
                event.getEventId(), tenantUserId, tenantKeycloakId, eventType,
                resp == null ? 0 : resp.results().size());
    }

    private void sendLegacyEmail(UtilityThresholdExceededEvent event) {
        if (!hasText(event.getLandlordUserId())) {
            return;
        }
        UserResponse recipient;
        try {
            recipient = userGrpcClient.getUserById(UUID.fromString(event.getLandlordUserId()));
        } catch (Exception e) {
            log.error("[UtilityAlert] legacy email lookup failed userId={}: {}",
                    event.getLandlordUserId(), e.getMessage());
            return;
        }
        if (recipient == null || !hasText(recipient.getEmail())) {
            log.warn("[UtilityAlert] no email for legacy recipient userId={}", event.getLandlordUserId());
            return;
        }

        Map<String, Object> vars = dispatchVars(event);
        vars.put("landlordName", safe(recipient.getName(), "you"));
        try {
            emailService.sendEmail(recipient.getEmail(), "utility_threshold_exceeded", LocaleType.vi_VN, vars);
        } catch (Exception e) {
            log.warn("[UtilityAlert] legacy email skipped userId={} eventId={}: {}",
                    event.getLandlordUserId(), event.getEventId(), e.getMessage());
        }
    }

    private Map<String, Object> dispatchVars(UtilityThresholdExceededEvent event) {
        LocaleType locale = LocaleType.vi_VN;
        Map<String, Object> vars = new HashMap<>();
        vars.put("houseName",    safe(event.getHouseName(), event.getHouseId()));
        vars.put("metricLabel",  metricLabel(event.getMetric(), locale));
        vars.put("currentUsage", formatNum(event.getCurrentUsage()));
        vars.put("monthlyLimit", formatNum(event.getMonthlyLimit()));
        vars.put("usagePercent", event.getUsagePercent() == null ? "—" : String.format("%.1f", event.getUsagePercent()));
        vars.put("unit",         safe(event.getUnit(), ""));
        vars.put("month",        safe(event.getMonth(), ""));
        vars.put("severity",     severityLabel(event.getCurrentStatus(), locale));
        vars.put("occurredAt",   event.getOccurredAt() != null
                ? DMY.format(Instant.ofEpochMilli(event.getOccurredAt()))
                : "—");
        return vars;
    }

    private static AlertEventType mapEventType(UtilityThresholdExceededEvent event) {
        boolean critical = "CRITICAL".equalsIgnoreCase(event.getCurrentStatus());
        boolean electricity = "ELECTRICITY".equalsIgnoreCase(event.getMetric());
        if (electricity) {
            return critical
                    ? AlertEventType.UTILITY_ELECTRICITY_CRITICAL
                    : AlertEventType.UTILITY_ELECTRICITY_WARNING;
        }
        return critical
                ? AlertEventType.UTILITY_WATER_CRITICAL
                : AlertEventType.UTILITY_WATER_WARNING;
    }

    private static String metricLabel(String metric, LocaleType locale) {
        if (metric == null) return "";
        boolean electricity = "ELECTRICITY".equalsIgnoreCase(metric);
        return switch (locale) {
            case en_US -> electricity ? "electricity" : "water";
            case ja_JP -> electricity ? "電気" : "水道";
            default    -> electricity ? "electricity" : "water";
        };
    }

    private static String severityLabel(String status, LocaleType locale) {
        boolean critical = "CRITICAL".equalsIgnoreCase(status);
        return switch (locale) {
            case en_US -> critical ? "CRITICAL" : "WARNING";
            case ja_JP -> critical ? "重大" : "警告";
            default    -> critical ? "CRITICAL" : "WARNING";
        };
    }

    private static String formatNum(Double v) {
        if (v == null) return "—";
        return NumberFormat.getNumberInstance(Locale.of("vi", "VN")).format(v);
    }

    private static String safe(String s, String fb) {
        return (s != null && !s.isBlank()) ? s.trim() : fb;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
