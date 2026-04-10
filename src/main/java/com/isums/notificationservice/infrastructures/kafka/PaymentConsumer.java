package com.isums.notificationservice.infrastructures.kafka;

import com.isums.notificationservice.domains.enums.LocaleType;
import com.isums.notificationservice.domains.enums.NotificationCategory;
import com.isums.notificationservice.domains.events.OverdueTerminationRequestedEvent;
import com.isums.notificationservice.domains.events.PowerCutConfirmedEvent;
import com.isums.notificationservice.domains.events.PowerCutRequestEvent;
import com.isums.notificationservice.domains.events.PowerCutReviewRequestedEvent;
import com.isums.notificationservice.infrastructures.abstracts.EmailService;
import com.isums.notificationservice.infrastructures.abstracts.ManagerNotificationService;
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
import tools.jackson.databind.ObjectMapper;

import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentConsumer {

    private final ManagerNotificationService notificationService;
    private final UserGrpcClient userGrpcClient;
    private final EmailService emailService;
    private final IdempotencyService idempotencyService;
    private final KafkaListenerHelper kafkaHelper;
    private final ObjectMapper objectMapper;

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DMY =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(VN);

    @KafkaListener(topics = "contract.power-cut-confirmed", groupId = "notification-group")
    public void handlePowerCutConfirmed(
            ConsumerRecord<String, String> record, Acknowledgment ack) {

        String messageId = kafkaHelper.extractMessageId(record);
        kafkaHelper.setupMDC(record, messageId);
        try {
            if (idempotencyService.isDuplicate(messageId)) {
                ack.acknowledge();
                return;
            }

            PowerCutConfirmedEvent event = objectMapper.readValue(
                    record.value(), PowerCutConfirmedEvent.class);

            UserResponse tenant = userGrpcClient.getUserById(event.getTenantId());

            emailService.sendEmail(
                    tenant.getEmail(),
                    "power_cut_warning_24h",
                    LocaleType.vi_VN,
                    Map.of("executeAt", DMY.format(event.getExecuteAt()))
            );

            idempotencyService.markProcessed(messageId);
            ack.acknowledge();
            log.info("[Notification] PowerCutWarning24h sent tenantId={}", event.getTenantId());
        } catch (Exception e) {
            log.error("[Notification] handlePowerCutConfirmed failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        } finally {
            kafkaHelper.clearMDC();
        }
    }

    @KafkaListener(topics = "contract.power-cut-review-requested",
            groupId = "notification-group")
    public void handlePowerCutReviewRequested(
            ConsumerRecord<String, String> record, Acknowledgment ack) {

        String messageId = kafkaHelper.extractMessageId(record);
        kafkaHelper.setupMDC(record, messageId);
        try {
            if (idempotencyService.isDuplicate(messageId)) {
                ack.acknowledge();
                return;
            }

            PowerCutReviewRequestedEvent event = objectMapper.readValue(
                    record.value(), PowerCutReviewRequestedEvent.class);

            notificationService.send(event.getManagerId(), NotificationCategory.PAYMENT_OVERDUE,
                    "Khách " + event.getTenantName()
                            + " trễ " + event.getDaysLate() + " ngày — Xem xét cắt điện",
                    "Tổng tiền cần thu: " + formatVnd(event.getTotalAmount())
                            + ". Vào hệ thống để xác nhận cắt điện nếu cần.",
                    "/contracts/" + event.getContractId() + "/power-cut",
                    Map.of(
                            "contractId", event.getContractId().toString(),
                            "daysLate", String.valueOf(event.getDaysLate())
                    )
            );

            idempotencyService.markProcessed(messageId);
            ack.acknowledge();
            log.info("[Notification] PowerCutReview notified managerId={}", event.getManagerId());
        } catch (Exception e) {
            log.error("[Notification] handlePowerCutReviewRequested failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        } finally {
            kafkaHelper.clearMDC();
        }
    }

    @KafkaListener(topics = "contract.termination-overdue-requested",
            groupId = "notification-group")
    public void handleOverdueTerminationRequested(
            ConsumerRecord<String, String> record, Acknowledgment ack) {

        String messageId = kafkaHelper.extractMessageId(record);
        kafkaHelper.setupMDC(record, messageId);
        try {
            if (idempotencyService.isDuplicate(messageId)) {
                ack.acknowledge();
                return;
            }

            OverdueTerminationRequestedEvent event = objectMapper.readValue(
                    record.value(), OverdueTerminationRequestedEvent.class);

            notificationService.send(event.getManagerId(), NotificationCategory.PAYMENT_OVERDUE,
                    "Khách " + event.getTenantName() + " trễ tiền thuê 30 ngày",
                    "Khách đã chậm thanh toán 30 ngày. Vui lòng xem xét chấm dứt hợp đồng.",
                    "/contracts/" + event.getContractId() + "/termination",
                    Map.of("contractId", event.getContractId().toString())
            );

            idempotencyService.markProcessed(messageId);
            ack.acknowledge();
            log.info("[Notification] OverdueTermination notified managerId={}", event.getManagerId());
        } catch (Exception e) {
            log.error("[Notification] handleOverdueTerminationRequested failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        } finally {
            kafkaHelper.clearMDC();
        }
    }

//    @KafkaListener(topics = "payment.power-cut-requested", groupId = "notification-group")
//    public void handlePowerCutRequest(
//            ConsumerRecord<String, String> record, Acknowledgment ack) {
//
//        String messageId = kafkaHelper.extractMessageId(record);
//        try {
//            if (idempotencyService.isDuplicate(messageId)) {
//                ack.acknowledge();
//                return;
//            }
//
//            PowerCutRequestEvent event = objectMapper.readValue(
//                    record.value(), PowerCutRequestEvent.class);
//
//            notificationService.send(event.getContractId(),
//                    NotificationCategory.PAYMENT_OVERDUE,
//                    "Tenant trễ tiền thuê 14 ngày — Xem xét cắt điện",
//                    "Khách thuê đã chậm thanh toán " + event.getDaysLate()
//                            + " ngày. Tổng tiền: " + formatVnd(event.getTotalAmount())
//                            + ". Bấm xác nhận nếu muốn cắt điện.",
//                    "/contracts/" + event.getContractId() + "/power-cut",
//                    Map.of(
//                            "contractId", event.getContractId().toString(),
//                            "invoiceId", event.getInvoiceId().toString(),
//                            "daysLate", String.valueOf(event.getDaysLate())
//                    )
//            );
//
//            idempotencyService.markProcessed(messageId);
//            ack.acknowledge();
//        } catch (Exception e) {
//            log.error("[Notification] handlePowerCutRequest failed: {}", e.getMessage(), e);
//            throw new RuntimeException(e);
//        }
//    }

    private String formatVnd(Long amount) {
        return NumberFormat.getNumberInstance(Locale.of("vi", "VN")).format(amount) + " ₫";
    }
}
