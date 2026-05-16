package com.isums.notificationservice.infrastructures.listeners;

import com.isums.notificationservice.domains.events.RenewalReminderEvent;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
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
        String messageId = kafkaHelper.extractMessageId(record);
        kafkaHelper.setupMDC(record, messageId);

        try {
            ConfirmAndSendToTenantEvent event = objectMapper.readValue(
                    record.value(), ConfirmAndSendToTenantEvent.class);
            if (event.getMessageId() != null) messageId = event.getMessageId();

            if (idempotencyService.isDuplicate(messageId)) {
                log.warn("[EContract] Duplicate skipped messageId={}", messageId);
                ack.acknowledge();
                return;
            }

            String recipientEmail = safe(event.getRecipientEmail(), null);
            String recipientName = safe(event.getRecipientName(), null);
            if ((recipientEmail == null || recipientEmail.isBlank()) && event.getRecipientUserId() == null) {
                log.error("[EContract] recipientUserId and recipientEmail null, skip. contractId={}",
                        event.getContractId());
                ack.acknowledge();
                return;
            }
            if (event.getUrl() == null || event.getUrl().isBlank()) {
                log.error("[EContract] url null/blank, skip. contractId={}", event.getContractId());
                ack.acknowledge();
                return;
            }

            if ((recipientEmail == null || recipientEmail.isBlank()) && event.getRecipientUserId() != null) {
                try {
                    UserResponse user = userGrpcClient.getUserById(event.getRecipientUserId());
                    if (user != null) {
                        recipientEmail = safe(user.getEmail(), null);
                        recipientName = safe(user.getName(), recipientName);
                    }
                } catch (StatusRuntimeException e) {
                    if (isPermanentGrpcFailure(e)) {
                        log.warn("[EContract] User lookup failed code={} userId={} contractId={}, using event email fallback: {}",
                                e.getStatus().getCode(), event.getRecipientUserId(), event.getContractId(), e.getMessage());
                        if (recipientEmail == null || recipientEmail.isBlank()) {
                            throw new IllegalStateException(
                                    "Recipient user not available yet and event has no email; retry later. userId="
                                            + event.getRecipientUserId());
                        }
                    } else {
                        throw e;
                    }
                }
            }
            if (recipientEmail == null || recipientEmail.isBlank()) {
                log.error("[EContract] recipientEmail unavailable, skip. userId={} contractId={}",
                        event.getRecipientUserId(), event.getContractId());
                idempotencyService.markProcessed(messageId);
                ack.acknowledge();
                return;
            }

            LocaleType locale = mapLocale(event.getContractLanguage());

            Map<String, Object> vars = new HashMap<>();
            vars.put("tenantName", safe(recipientName, fallbackTenantName(locale)));
            vars.put("contractName", safe(event.getContractName(), fallbackContractName(locale)));
            vars.put("contractNo", shortId(event.getContractId()));
            vars.put("propertyAddress", "N/A");
            vars.put("startDate", formatDate(event.getStartDate()));
            vars.put("endDate", formatDate(event.getEndDate()));
            vars.put("viewUrl", event.getUrl());
            vars.put("confirmUrl", safe(event.getConfirmUrl(), event.getUrl()));
            vars.put("expiresIn", expiresIn(locale));
            vars.put("landlordName", fallbackLandlordName(locale));

            emailService.sendEmail(recipientEmail, "econtract_view_confirm", locale, vars);

            idempotencyService.markProcessed(messageId);
            ack.acknowledge();

            log.info("[EContract] Email sent messageId={} to={} contractId={}",
                    messageId, recipientEmail, event.getContractId());

        } catch (JacksonException e) {
            log.error("[EContract] Deserialization failed messageId={} raw={}: {}",
                    messageId, record.value(), e.getMessage());
            ack.acknowledge();
        } catch (StatusRuntimeException e) {
            if (isPermanentGrpcFailure(e)) {
                log.warn("[EContract] Permanent gRPC failure code={} messageId={}: {} — ack and skip",
                        e.getStatus().getCode(), messageId, e.getMessage());
                idempotencyService.markProcessed(messageId);
                ack.acknowledge();
            } else {
                log.error("[EContract] Transient gRPC failure code={} messageId={}, will retry: {}",
                        e.getStatus().getCode(), messageId, e.getMessage());
                throw e;
            }
        } catch (Exception e) {
            log.error("[EContract] Processing failed messageId={}, will retry: {}",
                    messageId, e.getMessage(), e);
            throw new RuntimeException(e);
        } finally {
            kafkaHelper.clearMDC();
        }
    }

    private static boolean isPermanentGrpcFailure(StatusRuntimeException e) {
        Status.Code code = e.getStatus().getCode();
        return code == Status.Code.NOT_FOUND
                || code == Status.Code.INVALID_ARGUMENT
                || code == Status.Code.PERMISSION_DENIED
                || code == Status.Code.UNAUTHENTICATED
                || code == Status.Code.FAILED_PRECONDITION;
    }

    @KafkaListener(topics = "contract.renewal.reminder", groupId = "notification-group")
    public void handleRenewalReminder(
            ConsumerRecord<String, String> record, Acknowledgment ack) {

        String messageId = kafkaHelper.extractMessageId(record);
        try {
            if (idempotencyService.isDuplicate(messageId)) {
                ack.acknowledge();
                return;
            }

            RenewalReminderEvent event = objectMapper.readValue(record.value(), RenewalReminderEvent.class);

            UserResponse tenant = userGrpcClient.getUserById(event.getTenantId());

            emailService.sendEmail(
                    tenant.getEmail(),
                    "contract_renewal_reminder",
                    LocaleType.vi_VN,
                    Map.of(
                            "tenantName", tenant.getName(),
                            "contractId", event.getContractId().toString()
                                    .substring(0, 8).toUpperCase(),
                            "daysRemaining", String.valueOf(event.getDaysRemaining()),
                            "endDate", DMY.format(event.getEndDate()),
                            "openForNew", event.getDaysRemaining() == 0
                    )
            );

            idempotencyService.markProcessed(messageId);
            ack.acknowledge();
            log.info("[Notification] RenewalReminder sent tenantId={} daysRemaining={}",
                    event.getTenantId(), event.getDaysRemaining());

        } catch (StatusRuntimeException e) {
            if (isPermanentGrpcFailure(e)) {
                log.warn("[Notification] RenewalReminder permanent gRPC failure code={}: {} — ack and skip",
                        e.getStatus().getCode(), e.getMessage());
                idempotencyService.markProcessed(messageId);
                ack.acknowledge();
            } else {
                log.error("[Notification] RenewalReminder transient gRPC failure code={}, will retry: {}",
                        e.getStatus().getCode(), e.getMessage());
                throw e;
            }
        } catch (Exception e) {
            log.error("[Notification] handleRenewalReminder failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
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

    private static LocaleType mapLocale(String contractLanguage) {
        if (contractLanguage == null) return LocaleType.vi_VN;
        return switch (contractLanguage) {
            case "VI_EN" -> LocaleType.en_US;
            case "VI_JA" -> LocaleType.ja_JP;
            default -> LocaleType.vi_VN;
        };
    }

    private static String fallbackTenantName(LocaleType l) {
        return switch (l) {
            case en_US -> "you";
            case ja_JP -> "お客様";
            default -> "you";
        };
    }

    private static String fallbackContractName(LocaleType l) {
        return switch (l) {
            case en_US -> "Lease contract";
            case ja_JP -> "賃貸借契約";
            default -> "House lease contract";
        };
    }

    private static String fallbackLandlordName(LocaleType l) {
        return switch (l) {
            case en_US -> "Landlord";
            case ja_JP -> "家主";
            default -> "Landlord";
        };
    }

    private static String expiresIn(LocaleType l) {
        return switch (l) {
            case en_US -> "24 hours";
            case ja_JP -> "24時間";
            default -> "24 hours";
        };
    }
}
