package com.isums.notificationservice.infrastructures.kafka;

import com.isums.notificationservice.domains.enums.NotificationCategory;
import com.isums.notificationservice.domains.events.IssueQuoteSubmittedEvent;
import com.isums.notificationservice.domains.events.IssueWorkSlotAssignedEvent;
import com.isums.notificationservice.infrastructures.abstracts.ManagerNotificationService;
import com.isums.notificationservice.infrastructures.grpcs.UserGrpcClient;
import com.isums.notificationservice.services.NotificationRecipientResolver;
import com.isums.userservice.grpc.UserResponse;
import common.kafkas.IdempotencyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class IssueNotificationEventListener {

    private final ManagerNotificationService notificationService;
    private final NotificationRecipientResolver recipientResolver;
    private final UserGrpcClient userGrpcClient;
    private final ObjectMapper objectMapper;
    private final IdempotencyService idempotencyService;

    @KafkaListener(topics = "job.assigned", groupId = "notification-group")
    public void handleIssueWorkSlotAssigned(String payload) {

        String messageId = KafkaPayloadFingerprint.of("job.assigned", payload);
        try {
            if (idempotencyService.isDuplicate(messageId)) {
                return;
            }

            IssueWorkSlotAssignedEvent event = objectMapper.readValue(
                    payload, IssueWorkSlotAssignedEvent.class);

            if (!"ISSUE".equalsIgnoreCase(event.getReferenceType())
                    || !"JOB_ASSIGNED".equalsIgnoreCase(event.getAction())) {
                return;
            }

            String staffName = resolveUserName(event.getStaffId(), "staff");
            List<UUID> recipientIds = recipientResolver.resolveLandlordAndManager(event.getHouseId(), event.getStaffId());

            Map<String, String> metadata = new HashMap<>();
            metadata.put("issueId", event.getReferenceId().toString());
            metadata.put("slotId", event.getSlotId().toString());
            metadata.put("houseId", event.getHouseId().toString());
            if (event.getStaffId() != null) {
                metadata.put("staffId", event.getStaffId().toString());
            }
            metadata.put("status", "SCHEDULED");

            for (UUID recipientId : recipientIds) {
                notificationService.send(
                        recipientId,
                        NotificationCategory.ISSUE_WORK_SLOT_CREATED,
                        "Work slot created for issue",
                        "Issue #" + shortId(event.getReferenceId())
                                + " has had a work slot scheduled with " + staffName + ".",
                        "/issues/" + event.getReferenceId(),
                        metadata
                );
            }

            idempotencyService.markProcessed(messageId);
            log.info("[Notification] handleIssueWorkSlotAssigned done messageId={}", messageId);
        } catch (Exception e) {
            log.error("[Notification] handleIssueWorkSlotAssigned failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "job.created", groupId = "notification-group")
    public void handleIssueCreated(String payload) {

        String messageId = KafkaPayloadFingerprint.of("job.created", payload);
        try {
            if (idempotencyService.isDuplicate(messageId)) {
                return;
            }

            IssueWorkSlotAssignedEvent event = objectMapper.readValue(
                    payload, IssueWorkSlotAssignedEvent.class);

            if (!"ISSUE".equalsIgnoreCase(event.getReferenceType())
                    || !"JOB_CREATED".equalsIgnoreCase(event.getAction())
                    || event.getTenantId() == null
                    || event.getReferenceId() == null) {
                return;
            }

            String actorName = resolveUserName(event.getTenantId(), "tenant");
            Map<String, String> metadata = new HashMap<>();
            metadata.put("issueId", event.getReferenceId().toString());
            metadata.put("houseId", event.getHouseId().toString());
            metadata.put("tenantId", event.getTenantId().toString());
            metadata.put("status", "CREATED");

            notificationService.send(
                    event.getTenantId(),
                    NotificationCategory.ISSUE_WORK_SLOT_CREATED,
                    "Issue created",
                    "Issue #" + shortId(event.getReferenceId())
                            + " has been created by " + actorName + ".",
                    "/issues/" + event.getReferenceId(),
                    metadata
            );

            idempotencyService.markProcessed(messageId);
            log.info("[Notification] handleIssueCreated done messageId={}", messageId);
        } catch (Exception e) {
            log.error("[Notification] handleIssueCreated failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    @KafkaListener(topics = "issue.quote.submitted", groupId = "notification-group")
    public void handleIssueQuoteSubmitted(String payload) {

        String messageId = KafkaPayloadFingerprint.of("issue.quote.submitted", payload);
        try {
            if (idempotencyService.isDuplicate(messageId)) {
                return;
            }

            IssueQuoteSubmittedEvent event = objectMapper.readValue(
                    payload, IssueQuoteSubmittedEvent.class);

            String staffName = resolveUserName(event.getStaffId(), "staff");
            List<UUID> recipientIds = recipientResolver.resolveLandlordAndManager(event.getHouseId());

            Map<String, String> metadata = new HashMap<>();
            metadata.put("issueId", event.getIssueId().toString());
            metadata.put("quoteId", event.getQuoteId().toString());
            metadata.put("houseId", event.getHouseId().toString());
            if (event.getStaffId() != null) {
                metadata.put("staffId", event.getStaffId().toString());
            }
            metadata.put("status", "WAITING_MANAGER_APPROVAL_QUOTE");
            if (event.getTotalPrice() != null) {
                metadata.put("totalPrice", event.getTotalPrice().toPlainString());
            }

            for (UUID recipientId : recipientIds) {
                notificationService.send(
                        recipientId,
                        NotificationCategory.ISSUE_QUOTE_WAITING_MANAGER_APPROVAL,
                        "Staff submitted a quote",
                        "Issue #" + shortId(event.getIssueId())
                                + " is awaiting quote approval from " + staffName + ".",
                        "/issues/" + event.getIssueId(),
                        metadata
                );
            }

            idempotencyService.markProcessed(messageId);
            log.info("[Notification] handleIssueQuoteSubmitted done messageId={}", messageId);
        } catch (Exception e) {
            log.error("[Notification] handleIssueQuoteSubmitted failed: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private String resolveUserName(UUID userId, String fallback) {
        if (userId == null) {
            return fallback;
        }
        try {
            UserResponse user = userGrpcClient.getUserById(userId);
            if (user != null && !user.getName().isBlank()) {
                return user.getName();
            }
        } catch (Exception e) {
            log.warn("[Notification] resolveUserName failed userId={}: {}", userId, e.getMessage());
        }
        return fallback;
    }

    private String shortId(UUID id) {
        return id.toString().substring(0, 8).toUpperCase();
    }
}

