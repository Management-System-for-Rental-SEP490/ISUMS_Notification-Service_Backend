package com.isums.notificationservice.infrastructures.listeners;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.isums.notificationservice.domains.events.UserActivatedEvent;
import com.isums.notificationservice.domains.events.SendEmailEvent;
import com.isums.notificationservice.domains.enums.LocaleType;
import com.isums.notificationservice.infrastructures.abstracts.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventListener {

    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy").withZone(VN);

    @KafkaListener(topics = "notification-email", groupId = "notification-group-v2",
            properties = {"auto.offset.reset:earliest"}, concurrency = "3")
    public void handleSendEmail(String payload) {
        log.info("[Email] handleSendEmail ENTRY len={}", payload != null ? payload.length() : -1);
        try {
            if (payload == null) {
                log.error("[Email] null payload, skipping");
                return;
            }
            SendEmailEvent event = objectMapper.readValue(payload, SendEmailEvent.class);

            if (event.to() == null || event.to().isBlank()) {
                log.error("[Email] Invalid event: missing 'to' field. raw={}", payload);
                return;
            }

            emailService.sendEmail(event.to(), event.templateCode().toLowerCase(), LocaleType.vi_VN,
                    event.params() != null ? event.params() : Map.of());

            log.info("[Email] email_dispatched to={} template={}", event.to(), event.templateCode());

        } catch (JacksonException e) {
            log.error("[Email] Deserialization failed raw={}: {}", payload, e.getMessage());
        } catch (Exception e) {
            log.error("[Email] Processing failed, will retry: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "user-activated-topic", groupId = "notification-group-v2",
            properties = {"auto.offset.reset:earliest"})
    public void handleOnUserActivated(String payload) {
        log.info("[Notification] handleOnUserActivated ENTRY len={}",
                payload != null ? payload.length() : -1);
        if (payload == null) {
            log.error("[Notification] user-activated null payload, skipping");
            return;
        }
        UserActivatedEvent event;
        try {
            event = objectMapper.readValue(payload, UserActivatedEvent.class);
        } catch (JacksonException e) {
            log.error("[Notification] user-activated deserialize failed, skip poison: {}", e.getMessage());
            return;
        } catch (Exception e) {
            log.error("[Notification] user-activated parse error, skip: {}", e.getMessage(), e);
            return;
        }

        if (event.email() == null || event.email().isBlank()) {
            log.error("[Notification] user-activated missing email, skip raw={}", payload);
            return;
        }

        try {
            Map<String, Object> params = new HashMap<>();
            params.put("name", event.name() != null ? event.name() : event.email());
            params.put("email", event.email());
            params.put("password", event.password() != null ? event.password() : "");
            params.put("hasInvoice", event.firstRentPaymentUrl() != null);

            if (event.firstRentPaymentUrl() != null) {
                params.put("invoiceType", "Tiền thuê tháng đầu");
                params.put("invoiceTypeVi", "Tiền thuê tháng đầu");
                params.put("invoiceTypeEn", "First-month rent");
                params.put("invoiceTypeJa", "初月家賃");
                params.put("invoiceTypeCode", "MONTHLY_RENT");
                params.put("invoiceAmount", formatVnd(event.firstRentAmount()));
                params.put("invoiceDueDate", event.firstRentDueDate() != null
                        ? DMY.format(event.firstRentDueDate()) : "");
                params.put("invoicePaymentUrl", event.firstRentPaymentUrl());
            }

            emailService.sendEmail(event.email(), "user_activated", resolveLocale(event.locale()), params);
            log.info("[Notification] USER_ACTIVATED sent to={}", event.email());

        } catch (Exception e) {
            log.warn("[Notification] handleOnUserActivated failed email={} - will retry: {}",
                    event.email(), e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private String formatVnd(Long amount) {
        if (amount == null) return "0 ₫";
        return NumberFormat.getNumberInstance(Locale.of("vi", "VN")).format(amount) + " ₫";
    }

    private LocaleType resolveLocale(String raw) {
        if (raw == null || raw.isBlank()) return LocaleType.vi_VN;
        try {
            return LocaleType.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            log.warn("[Notification] Unknown locale '{}' on user-activated event, falling back to vi_VN", raw);
            return LocaleType.vi_VN;
        }
    }
}
