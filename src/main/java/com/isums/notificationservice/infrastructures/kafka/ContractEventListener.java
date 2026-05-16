package com.isums.notificationservice.infrastructures.kafka;

import com.isums.notificationservice.domains.enums.NotificationCategory;
import com.isums.notificationservice.domains.events.ContractCancelledByTenantEvent;
import com.isums.notificationservice.domains.events.ContractCompletedEvent;
import com.isums.notificationservice.domains.events.ContractReadyForLandlordSignatureEvent;
import com.isums.notificationservice.domains.events.DepositRefundConfirmedEvent;
import com.isums.notificationservice.domains.events.InspectionDoneNotifyEvent;
import com.isums.notificationservice.domains.events.InspectionScheduledEvent;
import com.isums.notificationservice.domains.enums.LocaleType;
import com.isums.notificationservice.infrastructures.abstracts.EmailService;
import com.isums.notificationservice.infrastructures.abstracts.ManagerNotificationService;
import com.isums.notificationservice.infrastructures.grpcs.UserGrpcClient;
import com.isums.notificationservice.services.NotificationRecipientResolver;
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
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ContractEventListener {

    private final ManagerNotificationService notificationService;
    private final EmailService emailService;
    private final UserGrpcClient userGrpcClient;
    private final NotificationRecipientResolver recipientResolver;
    private final ObjectMapper objectMapper;
    private final IdempotencyService idempotencyService;
    private final KafkaListenerHelper kafkaHelper;

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

            Map<String, String> metadata = new HashMap<>();
            metadata.put("contractId", event.getContractId().toString());
            metadata.put("inspectionId", event.getInspectionId().toString());
            metadata.put("status", "PENDING_TERMINATION");
            if (event.getHouseId() != null) {
                metadata.put("houseId", event.getHouseId().toString());
            }

            List<UUID> recipientIds = recipientResolver.resolveLandlordAndManager(
                    event.getHouseId(), event.getManagerId());
            for (UUID recipientId : recipientIds) {
                notificationService.send(
                        recipientId,
                        NotificationCategory.CONTRACT_EXPIRED,
                        "Hợp đồng hết hạn — đã lên lịch kiểm tra nhà",
                        "Hợp đồng #" + event.getContractId().toString().substring(0, 8).toUpperCase()
                                + " của khách thuê " + event.getTenantName()
                                + " đã hết hạn. Đã phân công nhân viên đi kiểm tra.",
                        "vi",
                        "/contracts/" + event.getContractId() + "/termination",
                        metadata
                );
            }

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
                    "Đã kiểm tra nhà xong — chờ xác nhận hoàn cọc",
                    "Nhân viên đã kiểm tra xong hợp đồng #"
                            + event.getContractId().toString().substring(0, 8).toUpperCase()
                            + ". Vui lòng xem lại và xác nhận số tiền cọc hoàn lại.",
                    "vi",
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

    @KafkaListener(topics = "contract.ready-for-landlord-signature",
            groupId = "notification-group")
    public void handleReadyForLandlordSignature(
            ConsumerRecord<String, String> record, Acknowledgment ack) {

        String messageId = kafkaHelper.extractMessageId(record);
        try {
            if (idempotencyService.isDuplicate(messageId)) {
                ack.acknowledge();
                return;
            }

            ContractReadyForLandlordSignatureEvent event = objectMapper.readValue(
                    record.value(), ContractReadyForLandlordSignatureEvent.class);

            String contractLabel = event.getContractName() != null && !event.getContractName().isBlank()
                    ? event.getContractName()
                    : "#" + event.getContractId().toString().substring(0, 8).toUpperCase();
            String tenantLabel = event.getTenantName() != null && !event.getTenantName().isBlank()
                    ? event.getTenantName()
                    : "khach thue";

            Map<String, String> metadata = new HashMap<>();
            metadata.put("contractId", event.getContractId().toString());
            metadata.put("status", "READY");
            if (event.getTenantId() != null) {
                metadata.put("tenantId", event.getTenantId().toString());
            }
            if (event.getDocumentId() != null && !event.getDocumentId().isBlank()) {
                metadata.put("documentId", event.getDocumentId());
            }
            if (event.getHouseId() != null) {
                metadata.put("houseId", event.getHouseId().toString());
            }

            List<UUID> recipientIds = recipientResolver.resolveLandlordAndManager(
                    event.getHouseId(), event.getRecipientUserId());
            if (recipientIds.isEmpty() && event.getRecipientUserId() != null) {
                recipientIds = List.of(event.getRecipientUserId());
            }

            String title = "Khách thuê đã xác nhận CCCD";
            String body = "Hợp đồng " + contractLabel + " của " + tenantLabel
                    + " đã sẵn sàng để chủ nhà ký.";
            String actionUrl = "/contracts/" + event.getContractId();

            for (UUID recipientId : recipientIds) {
                notificationService.send(
                        recipientId,
                        NotificationCategory.CONTRACT_READY_FOR_LANDLORD_SIGNATURE,
                        title,
                        body,
                        "vi",
                        actionUrl,
                        metadata
                );
                sendReadyForLandlordSignatureEmail(recipientId, event, contractLabel, tenantLabel, actionUrl);
            }

            idempotencyService.markProcessed(messageId);
            ack.acknowledge();
            log.info("[Notification] handleReadyForLandlordSignature done messageId={}", messageId);
        } catch (Exception e) {
            log.error("[Notification] handleReadyForLandlordSignature failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "contract.deposit-refund.confirmed",
            groupId = "notification-group")
    public void handleDepositRefundConfirmed(
            ConsumerRecord<String, String> record, Acknowledgment ack) {

        String messageId = kafkaHelper.extractMessageId(record);
        try {
            if (idempotencyService.isDuplicate(messageId)) {
                ack.acknowledge();
                return;
            }

            DepositRefundConfirmedEvent event = objectMapper.readValue(
                    record.value(), DepositRefundConfirmedEvent.class);

            Map<String, String> metadata = new HashMap<>();
            metadata.put("contractId", event.getContractId().toString());
            metadata.put("status", "DEPOSIT_REFUND_PENDING");
            metadata.put("refundAmount", String.valueOf(event.getRefundAmount()));
            if (event.getHouseId() != null) {
                metadata.put("houseId", event.getHouseId().toString());
            }
            if (event.getTenantId() != null) {
                metadata.put("tenantId", event.getTenantId().toString());
            }

            List<UUID> recipientIds = recipientResolver.resolveLandlordAndManager(event.getHouseId());
            for (UUID recipientId : recipientIds) {
                notificationService.send(
                        recipientId,
                        NotificationCategory.DEPOSIT_REFUND_CONFIRM,
                        "Đã ghi nhận yêu cầu hoàn cọc",
                        "Khoản hoàn cọc hợp đồng #" + shortId(event.getContractId())
                                + " đã được tạo. Vui lòng theo dõi và xác nhận khi đã chuyển tiền.",
                        "vi",
                        "/contracts/" + event.getContractId() + "/deposit-refund",
                        metadata
                );
            }

            idempotencyService.markProcessed(messageId);
            ack.acknowledge();
            log.info("[Notification] handleDepositRefundConfirmed done messageId={} recipients={}",
                    messageId, recipientIds.size());
        } catch (Exception e) {
            log.error("[Notification] handleDepositRefundConfirmed failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private void sendReadyForLandlordSignatureEmail(
            UUID recipientId,
            ContractReadyForLandlordSignatureEvent event,
            String contractLabel,
            String tenantLabel,
            String actionUrl) {
        try {
            UserResponse recipient = userGrpcClient.getUserById(recipientId);
            if (recipient == null || recipient.getEmail() == null || recipient.getEmail().isBlank()) {
                log.warn("[Notification] Skip ready email, recipient email missing recipientId={} contractId={}",
                        recipientId, event.getContractId());
                return;
            }

            emailService.sendEmail(
                    recipient.getEmail(),
                    "econtract_ready_for_landlord_signature",
                    LocaleType.vi_VN,
                    Map.of(
                            "recipientName", safe(recipient.getName(), "anh/chị"),
                            "tenantName", tenantLabel,
                            "contractName", contractLabel,
                            "contractNo", shortId(event.getContractId()),
                            "actionUrl", actionUrl
                    )
            );
        } catch (StatusRuntimeException e) {
            if (isPermanentGrpcFailure(e)) {
                log.warn("[Notification] Skip ready email, user lookup failed code={} recipientId={} contractId={}: {}",
                        e.getStatus().getCode(), recipientId, event.getContractId(), e.getMessage());
                return;
            }
            throw e;
        }
    }

    @KafkaListener(topics = "contract-completed-topic",
            groupId = "notification-group")
    public void handleContractCompleted(
            ConsumerRecord<String, String> record, Acknowledgment ack) {

        String messageId = kafkaHelper.extractMessageId(record);
        try {
            if (idempotencyService.isDuplicate(messageId)) {
                ack.acknowledge();
                return;
            }

            ContractCompletedEvent event = objectMapper.readValue(
                    record.value(), ContractCompletedEvent.class);

            String contractLabel = "#" + event.getContractId().toString().substring(0, 8).toUpperCase();
            String tenantLabel = event.getTenantEmail() != null && !event.getTenantEmail().isBlank()
                    ? event.getTenantEmail()
                    : "khach thue";

            Map<String, String> metadata = new HashMap<>();
            metadata.put("contractId", event.getContractId().toString());
            metadata.put("status", "COMPLETED");
            if (event.getTenantId() != null) {
                metadata.put("tenantId", event.getTenantId().toString());
            }
            if (event.getHouseId() != null) {
                metadata.put("houseId", event.getHouseId().toString());
            }
            if (event.getCompletedAt() != null) {
                metadata.put("completedAt", event.getCompletedAt().toString());
            }
            if (event.getSignedPdfUrl() != null && !event.getSignedPdfUrl().isBlank()) {
                metadata.put("signedPdfUrl", event.getSignedPdfUrl());
            }

            List<UUID> recipientIds = recipientResolver.resolveLandlordAndManager(
                    event.getHouseId(), event.getLandlordId());
            for (UUID recipientId : recipientIds) {
                notificationService.send(
                        recipientId,
                        NotificationCategory.CONTRACT_COMPLETED,
                        "Hợp đồng được ký thành công",
                        "Người thuê nhà " + tenantLabel + " đã hoàn tất việc ký hợp đồng " + contractLabel + ".",
                        "vi",
                        "/contracts/" + event.getContractId(),
                        metadata
                );
            }

            idempotencyService.markProcessed(messageId);
            ack.acknowledge();
            log.info("[Notification] handleContractCompleted done messageId={}", messageId);
        } catch (Exception e) {
            log.error("[Notification] handleContractCompleted failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "contract.cancelled-by-tenant",
            groupId = "notification-group")
    public void handleContractCancelledByTenant(
            ConsumerRecord<String, String> record, Acknowledgment ack) {

        String messageId = kafkaHelper.extractMessageId(record);
        try {
            if (idempotencyService.isDuplicate(messageId)) {
                ack.acknowledge();
                return;
            }

            ContractCancelledByTenantEvent event = objectMapper.readValue(
                    record.value(), ContractCancelledByTenantEvent.class);

            String tenantLabel = event.getTenantName() != null && !event.getTenantName().isBlank()
                    ? event.getTenantName()
                    : "khach thue";

            Map<String, String> metadata = new HashMap<>();
            metadata.put("contractId", event.getContractId().toString());
            metadata.put("status", "CANCELLED_BY_TENANT");
            if (event.getHouseId() != null) {
                metadata.put("houseId", event.getHouseId().toString());
            }
            if (event.getTenantId() != null) {
                metadata.put("tenantId", event.getTenantId().toString());
            }
            if (event.getCancelledAt() != null) {
                metadata.put("cancelledAt", event.getCancelledAt().toString());
            }
            if (event.getReason() != null && !event.getReason().isBlank()) {
                metadata.put("reason", event.getReason());
            }

            List<UUID> recipientIds = recipientResolver.resolveLandlordAndManager(
                    event.getHouseId(), event.getInitiatedByUserId());
            for (UUID recipientId : recipientIds) {
                notificationService.send(
                        recipientId,
                        NotificationCategory.CONTRACT_CANCELLED_BY_TENANT,
                        "Khách thuê đã huỷ ký hợp đồng",
                        "Khách thuê " + tenantLabel + " đã huỷ ký hợp đồng #"
                                + event.getContractId().toString().substring(0, 8).toUpperCase() + ".",
                        "vi",
                        "/contracts/" + event.getContractId(),
                        metadata
                );
            }

            idempotencyService.markProcessed(messageId);
            ack.acknowledge();
            log.info("[Notification] handleContractCancelledByTenant done messageId={}", messageId);
        } catch (Exception e) {
            log.error("[Notification] handleContractCancelledByTenant failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
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

    private static String safe(String value, String fallback) {
        return value != null && !value.isBlank() ? value.trim() : fallback;
    }

    private static String shortId(UUID id) {
        return id != null ? id.toString().substring(0, 8).toUpperCase() : "N/A";
    }
}
