package com.isums.notificationservice.infrastructures.listeners;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.isums.notificationservice.domains.events.ConfirmAndSendToTenantEvent;
import com.isums.notificationservice.domains.enums.LocaleType;
import com.isums.notificationservice.infrastructures.abstracts.EmailService;
import com.isums.notificationservice.infrastructures.grpcs.UserGrpcClient;
import com.isums.userservice.grpc.UserResponse;
import common.kafkas.IdempotencyService;
import common.kafkas.KafkaListenerHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class EContractEventListener {

    private final EmailService emailService;
    private final UserGrpcClient userGrpcClient;
    private final IdempotencyService idempotencyService;
    private final KafkaListenerHelper kafkaHelper;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter DMY = DateTimeFormatter
            .ofPattern("dd/MM/yyyy")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    @KafkaListener(topics = "confirmAndSendToTenant-topic", groupId = "notification-group")
    public void handleConfirmAndSendToTenant(ConsumerRecord<String, String> record, Acknowledgment ack) {

        ConfirmAndSendToTenantEvent event = objectMapper.readValue(record.value(), ConfirmAndSendToTenantEvent.class);
        String messageId = event.getMessageId();
        kafkaHelper.setupMDC(record, messageId);

        try {
            if (idempotencyService.isDuplicate(messageId)) {
                log.warn("[EContract] Duplicate skipped messageId={}", messageId);
                ack.acknowledge();
                return;
            }

            if (event.getRecipientUserId() == null) {
                log.error("[EContract] recipientUserId null, skip. contractId={}", event.getContractId());
                ack.acknowledge();
                return;
            }
            if (event.getUrl() == null || event.getUrl().isBlank()) {
                log.error("[EContract] url null/blank, skip. contractId={}", event.getContractId());
                ack.acknowledge();
                return;
            }

            UserResponse user = userGrpcClient.getUserById(event.getRecipientUserId());
            if (user == null) {
                log.error("[EContract] User not found userId={} contractId={}",
                        event.getRecipientUserId(), event.getContractId());
                ack.acknowledge();
                return;
            }

            Map<String, Object> vars = new HashMap<>();
            vars.put("tenantName", safe(user.getName(), "bạn"));
            vars.put("contractName", safe(event.getContractName(), "Hợp đồng thuê nhà"));
            vars.put("contractNo", shortId(event.getContractId()));
            vars.put("propertyAddress", "N/A");
            vars.put("startDate", formatDate(event.getStartDate()));
            vars.put("endDate", formatDate(event.getEndDate()));
            vars.put("viewUrl", event.getUrl());
            vars.put("confirmUrl", safe(event.getConfirmUrl(), event.getUrl()));
            vars.put("expiresIn", "24 giờ");
            vars.put("landlordName", "Chủ nhà");

            emailService.sendEmail(user.getEmail(), "econtract_view_confirm", LocaleType.vi_VN, vars);

            idempotencyService.markProcessed(messageId);
            ack.acknowledge();

            log.info("[EContract] Email sent messageId={} to={} contractId={}",
                    messageId, user.getEmail(), event.getContractId());

        } catch (JacksonException e) {
            log.error("[EContract] Deserialization failed messageId={} raw={}: {}",
                    messageId, record.value(), e.getMessage());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[EContract] Processing failed messageId={}, will retry: {}",
                    messageId, e.getMessage(), e);
            throw new RuntimeException(e);
        } finally {
            kafkaHelper.clearMDC();
        }
    }

    private String safe(String s, String fallback) {
        return (s != null && !s.isBlank()) ? s.trim() : fallback;
    }

    private String formatDate(Instant instant) {
        return instant != null ? DMY.format(instant) : "N/A";
    }

    private String shortId(java.util.UUID id) {
        return id != null ? id.toString().substring(0, 8).toUpperCase() : "N/A";
    }
}