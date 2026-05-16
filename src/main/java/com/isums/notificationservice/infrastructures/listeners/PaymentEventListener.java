package com.isums.notificationservice.infrastructures.listeners;

import com.isums.notificationservice.domains.events.DepositPaidEvent;
import com.isums.notificationservice.domains.events.DepositRefundPaidEvent;
import com.isums.notificationservice.domains.events.SendEmailEvent;
import com.isums.notificationservice.domains.enums.LocaleType;
import com.isums.notificationservice.infrastructures.abstracts.EmailService;
import com.isums.notificationservice.infrastructures.grpcs.UserGrpcClient;
import com.isums.userservice.grpc.UserResponse;
import common.kafkas.IdempotencyService;
import common.kafkas.KafkaListenerHelper;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final EmailService emailService;
    private final UserGrpcClient userGrpcClient;
    private final IdempotencyService idempotencyService;
    private final KafkaListenerHelper kafkaHelper;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter DMY = DateTimeFormatter
            .ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    @KafkaListener(topics = "payment-paid-topic", groupId = "notification-group")
    public void handlePaymentPaid(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String messageId = kafkaHelper.extractMessageId(record);
        kafkaHelper.setupMDC(record, messageId);

        try {
            if (idempotencyService.isDuplicate(messageId)) {
                log.warn("[Payment] Duplicate skipped messageId={}", messageId);
                ack.acknowledge();
                return;
            }

            DepositPaidEvent event = objectMapper.readValue(record.value(), DepositPaidEvent.class);

            String recipientEmail = safe(event.tenantEmail(), null);
            String recipientName = "you";

            if (event.tenantId() != null) {
                try {
                    UserResponse user = userGrpcClient.getUserById(event.tenantId());
                    if (user != null) {
                        if (recipientEmail == null || recipientEmail.isBlank()) {
                            recipientEmail = safe(user.getEmail(), null);
                        }
                        recipientName = safe(user.getName(), recipientName);
                    }
                } catch (StatusRuntimeException e) {
                    if (isPermanentGrpcFailure(e)) {
                        log.warn("[Payment] User lookup failed code={} tenantId={}, using event email fallback={}",
                                e.getStatus().getCode(), event.tenantId(), recipientEmail);
                    } else {
                        throw e;
                    }
                }
            }

            if (recipientEmail == null || recipientEmail.isBlank()) {
                log.error("[Payment] Receipt email skipped, recipient unavailable tenantId={} invoiceId={}",
                        event.tenantId(), event.invoiceId());
                idempotencyService.markProcessed(messageId);
                ack.acknowledge();
                return;
            }

            Map<String, Object> vars = new HashMap<>();
            vars.put("tenantName", recipientName);
            vars.put("invoiceType", translateType(event.invoiceType()));
            vars.put("amount", formatVnd(event.amount()));
            vars.put("txnNo", event.txnNo());
            vars.put("paidAt", event.paidAt() != null ? DMY.format(event.paidAt()) : "N/A");

            emailService.sendEmail(recipientEmail, "payment_receipt", LocaleType.vi_VN, vars);

            idempotencyService.markProcessed(messageId);
            ack.acknowledge();

            log.info("[Payment] Receipt email sent messageId={} to={} type={}",
                    messageId, recipientEmail, event.invoiceType());

        } catch (JacksonException e) {
            log.error("[Payment] Deserialize failed messageId={}: {}", messageId, e.getMessage());
            ack.acknowledge();
        } catch (StatusRuntimeException e) {
            log.error("[Payment] Transient gRPC failure code={} messageId={}, will retry: {}",
                    e.getStatus().getCode(), messageId, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("[Payment] Processing failed messageId={}, will retry: {}",
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

    @KafkaListener(topics = "deposit-refund-paid-topic", groupId = "notification-group")
    public void handleDepositRefundPaid(ConsumerRecord<String, String> record, Acknowledgment ack) {
        String messageId = kafkaHelper.extractMessageId(record);
        kafkaHelper.setupMDC(record, messageId);

        try {
            if (idempotencyService.isDuplicate(messageId)) {
                log.warn("[Payment] Duplicate refund-paid skipped messageId={}", messageId);
                ack.acknowledge();
                return;
            }

            DepositRefundPaidEvent event = objectMapper.readValue(record.value(), DepositRefundPaidEvent.class);

            String recipientEmail = safe(event.getTenantEmail(), null);
            String tenantName = "you";
            if ((recipientEmail == null || recipientEmail.isBlank()) && event.getTenantId() != null) {
                UserResponse user = userGrpcClient.getUserById(event.getTenantId());
                if (user != null) {
                    recipientEmail = safe(user.getEmail(), null);
                    tenantName = safe(user.getName(), tenantName);
                }
            }

            if (recipientEmail == null || recipientEmail.isBlank()) {
                log.error("[Payment] Deposit refund paid email skipped, recipient unavailable tenantId={} contractId={}",
                        event.getTenantId(), event.getContractId());
                idempotencyService.markProcessed(messageId);
                ack.acknowledge();
                return;
            }

            Map<String, Object> vars = new HashMap<>();
            vars.put("tenantName", tenantName);
            vars.put("contractId", shortId(event.getContractId()));
            vars.put("refundAmount", formatVnd(event.getRefundAmount()));
            vars.put("paymentMethod", safe(event.getPaymentMethod(), "N/A"));
            vars.put("paidAt", event.getPaidAt() != null ? DMY.format(event.getPaidAt()) : "N/A");
            vars.put("note", safe(event.getNote(), ""));

            emailService.sendEmail(recipientEmail, "deposit_refund_paid_notify", LocaleType.vi_VN, vars);

            idempotencyService.markProcessed(messageId);
            ack.acknowledge();

            log.info("[Payment] Deposit refund paid email sent messageId={} to={} contractId={}",
                    messageId, recipientEmail, event.getContractId());
        } catch (JacksonException e) {
            log.error("[Payment] Deposit refund paid deserialize failed messageId={}: {}", messageId, e.getMessage());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[Payment] Deposit refund paid processing failed messageId={}, will retry: {}",
                    messageId, e.getMessage(), e);
            throw new RuntimeException(e);
        } finally {
            kafkaHelper.clearMDC();
        }
    }

    private String translateType(String type) {
        return switch (type) {
            case "DEPOSIT" -> "Deposit";
            case "MONTHLY_RENT" -> "Monthly rent";
            case "MAINTENANCE" -> "Repair fee";
            case "UTILITY" -> "Utility fee";
            case "PENALTY" -> "Penalty";
            default -> "Invoice";
        };
    }

    private String formatVnd(Long amount) {
        if (amount == null) return "0 ₫";
        return NumberFormat.getNumberInstance(Locale.of("vi", "VN")).format(amount) + " ₫";
    }

    private String safe(String s, String fb) {
        return (s != null && !s.isBlank()) ? s.trim() : fb;
    }

    private String shortId(java.util.UUID id) {
        return id != null ? id.toString().substring(0, 8).toUpperCase() : "N/A";
    }
}
