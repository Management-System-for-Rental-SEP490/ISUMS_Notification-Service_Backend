package com.isums.notificationservice.infrastructures.kafka;

import com.isums.notificationservice.domains.enums.NotificationCategory;
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
}