package com.isums.notificationservice.infrastructures.listeners;

import com.isums.notificationservice.domains.events.DepositPaidEvent;
import com.isums.notificationservice.domains.events.SendEmailEvent;
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

            UserResponse user = userGrpcClient.getUserById(event.tenantId());
            if (user == null) {
                log.error("[Payment] User not found tenantId={}", event.tenantId());
                ack.acknowledge();
                return;
            }

            Map<String, Object> vars = new HashMap<>();
            vars.put("tenantName", safe(user.getName(), "bạn"));
            vars.put("invoiceType", translateType(event.invoiceType()));
            vars.put("amount", formatVnd(event.amount()));
            vars.put("txnNo", event.txnNo());
            vars.put("paidAt", event.paidAt() != null ? DMY.format(event.paidAt()) : "N/A");

            emailService.sendEmail(user.getEmail(), "payment_receipt", LocaleType.vi_VN, vars);

            idempotencyService.markProcessed(messageId);
            ack.acknowledge();

            log.info("[Payment] Receipt email sent messageId={} to={} type={}",
                    messageId, user.getEmail(), event.invoiceType());

        } catch (JacksonException e) {
            log.error("[Payment] Deserialize failed messageId={}: {}", messageId, e.getMessage());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[Payment] Processing failed messageId={}, will retry: {}",
                    messageId, e.getMessage(), e);
            throw new RuntimeException(e);
        } finally {
            kafkaHelper.clearMDC();
        }
    }

    private String translateType(String type) {
        return switch (type) {
            case "DEPOSIT" -> "Tiền cọc";
            case "MONTHLY_RENT" -> "Tiền thuê tháng";
            case "MAINTENANCE" -> "Phí sửa chữa";
            case "UTILITY" -> "Phí tiện ích";
            case "PENALTY" -> "Tiền phạt";
            default -> "Hóa đơn";
        };
    }

    private String formatVnd(Long amount) {
        if (amount == null) return "0 ₫";
        return NumberFormat.getNumberInstance(Locale.of("vi", "VN")).format(amount) + " ₫";
    }

    private String safe(String s, String fb) {
        return (s != null && !s.isBlank()) ? s.trim() : fb;
    }
}