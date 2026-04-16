package com.isums.notificationservice.infrastructures.kafka;

import com.isums.notificationservice.domains.enums.NotificationCategory;
import com.isums.notificationservice.domains.events.ContractReadyForLandlordSignatureEvent;
import com.isums.notificationservice.domains.events.InspectionDoneNotifyEvent;
import com.isums.notificationservice.domains.events.InspectionScheduledEvent;
import com.isums.notificationservice.infrastructures.abstracts.ManagerNotificationService;
import common.kafkas.IdempotencyService;
import common.kafkas.KafkaListenerHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContractEventListener {

    private final ManagerNotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final IdempotencyService idempotencyService;
    private final KafkaListenerHelper kafkaHelper;

    @KafkaListener(topics = "contract.ready-for-landlord-signature",
            groupId = "notification-group")
    public void handleReadyForLandlordSignature(
            ConsumerRecord<String, String> record, Acknowledgment ack) {

        String messageId = kafkaHelper.extractMessageId(record);
        try {
            ContractReadyForLandlordSignatureEvent event = objectMapper.readValue(
                    record.value(), ContractReadyForLandlordSignatureEvent.class);
            if (event.getMessageId() != null) messageId = event.getMessageId();

            if (idempotencyService.isDuplicate(messageId)) {
                ack.acknowledge();
                return;
            }

            notificationService.send(
                    event.getRecipientId(),
                    NotificationCategory.CONTRACT_READY_FOR_LANDLORD_SIGNATURE,
                    "Khách đã xác nhận CCCD - Chờ chủ nhà ký",
                    "Khách " + safe(event.getTenantName(), "người thuê")
                            + " đã xác nhận CCCD cho hợp đồng #"
                            + shortId(event.getContractId())
                            + ". Vào hệ thống để ký tiếp.",
                    "/contracts/" + event.getContractId(),
                    Map.of("contractId", event.getContractId().toString())
            );

            idempotencyService.markProcessed(messageId);
            ack.acknowledge();
            log.info("[Notification] handleReadyForLandlordSignature done messageId={}", messageId);
        } catch (Exception e) {
            log.error("[Notification] handleReadyForLandlordSignature failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    // Hợp đồng hết hạn → phân công nhân viên kiểm tra
    @KafkaListener(topics = "contract.inspection.scheduled",
            groupId = "notification-group")
    public void handleInspectionScheduled(
            ConsumerRecord<String, String> record, Acknowledgment ack) {

        String messageId = kafkaHelper.extractMessageId(record);
        try {
            if (idempotencyService.isDuplicate(messageId)) {
                ack.acknowledge();
                return;
            }

            InspectionScheduledEvent event = objectMapper.readValue(
                    record.value(), InspectionScheduledEvent.class);

            notificationService.send(
                    event.getManagerId(),
                    NotificationCategory.CONTRACT_EXPIRED,
                    "Hợp đồng hết hạn — Đã lên lịch kiểm tra nhà",
                    "Hợp đồng #" + event.getContractId().toString().substring(0, 8).toUpperCase()
                            + " của khách " + event.getTenantName()
                            + " đã hết hạn. Nhân viên đã được phân công kiểm tra.",
                    "/contracts/" + event.getContractId() + "/termination",
                    Map.of(
                            "contractId", event.getContractId().toString(),
                            "inspectionId", event.getInspectionId().toString()
                    )
            );

            idempotencyService.markProcessed(messageId);
            ack.acknowledge();
            log.info("[Notification] handleInspectionScheduled done messageId={}", messageId);
        } catch (Exception e) {
            log.error("[Notification] handleInspectionScheduled failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "contract.inspection.done",
            groupId = "notification-group")
    public void handleInspectionDone(
            ConsumerRecord<String, String> record, Acknowledgment ack) {

        String messageId = kafkaHelper.extractMessageId(record);
        try {
            if (idempotencyService.isDuplicate(messageId)) {
                ack.acknowledge();
                return;
            }

            InspectionDoneNotifyEvent event = objectMapper.readValue(record.value(), InspectionDoneNotifyEvent.class);

            notificationService.send(event.getManagerId(), NotificationCategory.INSPECTION_DONE,
                    "Kiểm tra nhà hoàn tất — Cần xác nhận hoàn cọc",
                    "Nhân viên đã kiểm tra xong hợp đồng #"
                            + event.getContractId().toString().substring(0, 8).toUpperCase()
                            + ". Vui lòng xem và xác nhận số tiền hoàn cọc.",
                    "/contracts/" + event.getContractId() + "/deposit-refund",
                    Map.of(
                            "contractId", event.getContractId().toString(),
                            "inspectionId", event.getInspectionId().toString(),
                            "deductionAmount", event.getDeductionAmount().toString()
                    )
            );

            idempotencyService.markProcessed(messageId);
            ack.acknowledge();
            log.info("[Notification] handleInspectionDone done messageId={}", messageId);
        } catch (Exception e) {
            log.error("[Notification] handleInspectionDone failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private String safe(String value, String fallback) {
        return value != null && !value.isBlank() ? value.trim() : fallback;
    }

    private String shortId(java.util.UUID id) {
        return id != null ? id.toString().substring(0, 8).toUpperCase() : "N/A";
    }
}
