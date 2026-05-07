package com.isums.notificationservice.infrastructures.kafka;

import com.isums.notificationservice.domains.enums.NotificationCategory;
import com.isums.notificationservice.domains.events.IssueQuoteSubmittedEvent;
import com.isums.notificationservice.domains.events.IssueWorkSlotAssignedEvent;
import com.isums.notificationservice.infrastructures.abstracts.ManagerNotificationService;
import com.isums.notificationservice.infrastructures.grpcs.UserGrpcClient;
import com.isums.notificationservice.services.NotificationRecipientResolver;
import com.isums.userservice.grpc.UserResponse;
import common.kafkas.IdempotencyService;
import common.kafkas.KafkaListenerHelper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("IssueNotificationEventListener")
class IssueNotificationEventListenerTest {

    @Mock private ManagerNotificationService notificationService;
    @Mock private NotificationRecipientResolver recipientResolver;
    @Mock private UserGrpcClient userGrpcClient;
    @Mock private ObjectMapper objectMapper;
    @Mock private IdempotencyService idempotencyService;
    @Mock private KafkaListenerHelper kafkaHelper;
    @Mock private Acknowledgment ack;

    @InjectMocks private IssueNotificationEventListener listener;

    @Nested
    @DisplayName("handleIssueWorkSlotAssigned")
    class IssueWorkSlotAssigned {

        private final ConsumerRecord<String, String> rec =
                new ConsumerRecord<>("job.assigned", 0, 0L, "k", "v");

        @Test
        @DisplayName("sends ISSUE_WORK_SLOT_CREATED to landlord and manager")
        void happy() throws Exception {
            UUID issueId = UUID.randomUUID();
            UUID houseId = UUID.randomUUID();
            UUID slotId = UUID.randomUUID();
            UUID staffId = UUID.randomUUID();
            UUID landlordId = UUID.randomUUID();
            UUID managerId = UUID.randomUUID();

            when(kafkaHelper.extractMessageId(rec)).thenReturn("m1");
            when(idempotencyService.isDuplicate("m1")).thenReturn(false);
            when(recipientResolver.resolveLandlordAndManager(houseId))
                    .thenReturn(List.of(landlordId, managerId));
            when(userGrpcClient.getUserById(staffId)).thenReturn(
                    UserResponse.newBuilder().setId(staffId.toString()).setName("Staff A").build());

            IssueWorkSlotAssignedEvent event = new IssueWorkSlotAssignedEvent(
                    issueId, null, houseId, slotId, staffId, "ISSUE", null, null, "JOB_ASSIGNED");
            when(objectMapper.readValue("v", IssueWorkSlotAssignedEvent.class)).thenReturn(event);

            listener.handleIssueWorkSlotAssigned(rec, ack);

            ArgumentCaptor<Map> metadataCap = ArgumentCaptor.forClass(Map.class);
            verify(notificationService, times(2)).send(
                    any(UUID.class),
                    eq(NotificationCategory.ISSUE_WORK_SLOT_CREATED),
                    any(),
                    any(),
                    eq("/issues/" + issueId),
                    metadataCap.capture());
            assertThat(metadataCap.getValue())
                    .containsEntry("issueId", issueId.toString())
                    .containsEntry("houseId", houseId.toString())
                    .containsEntry("slotId", slotId.toString())
                    .containsEntry("staffId", staffId.toString())
                    .containsEntry("status", "SCHEDULED");
            verify(ack).acknowledge();
        }
    }

    @Nested
    @DisplayName("handleIssueCreated")
    class IssueCreated {

        private final ConsumerRecord<String, String> rec =
                new ConsumerRecord<>("job.created", 0, 0L, "k", "v");

        @Test
        @DisplayName("sends ISSUE_WORK_SLOT_CREATED to tenant when issue is created")
        void happy() throws Exception {
            UUID issueId = UUID.randomUUID();
            UUID houseId = UUID.randomUUID();
            UUID tenantId = UUID.randomUUID();

            when(kafkaHelper.extractMessageId(rec)).thenReturn("m1");
            when(idempotencyService.isDuplicate("m1")).thenReturn(false);
            when(userGrpcClient.getUserById(tenantId)).thenReturn(
                    UserResponse.newBuilder().setId(tenantId.toString()).setName("Tenant A").build());

            IssueWorkSlotAssignedEvent event = new IssueWorkSlotAssignedEvent(
                    issueId, tenantId, houseId, null, null, "ISSUE", null, null, "JOB_CREATED");
            when(objectMapper.readValue("v", IssueWorkSlotAssignedEvent.class)).thenReturn(event);

            listener.handleIssueCreated(rec, ack);

            ArgumentCaptor<Map> metadataCap = ArgumentCaptor.forClass(Map.class);
            verify(notificationService).send(
                    eq(tenantId),
                    eq(NotificationCategory.ISSUE_WORK_SLOT_CREATED),
                    any(),
                    any(),
                    eq("/issues/" + issueId),
                    metadataCap.capture());
            assertThat(metadataCap.getValue())
                    .containsEntry("issueId", issueId.toString())
                    .containsEntry("houseId", houseId.toString())
                    .containsEntry("tenantId", tenantId.toString())
                    .containsEntry("status", "CREATED");
            verify(ack).acknowledge();
        }
    }

    @Nested
    @DisplayName("handleIssueQuoteSubmitted")
    class IssueQuoteSubmitted {

        private final ConsumerRecord<String, String> rec =
                new ConsumerRecord<>("issue.quote.submitted", 0, 0L, "k", "v");

        @Test
        @DisplayName("sends ISSUE_QUOTE_WAITING_MANAGER_APPROVAL to landlord and manager")
        void happy() throws Exception {
            UUID issueId = UUID.randomUUID();
            UUID quoteId = UUID.randomUUID();
            UUID houseId = UUID.randomUUID();
            UUID staffId = UUID.randomUUID();
            UUID landlordId = UUID.randomUUID();
            UUID managerId = UUID.randomUUID();

            when(kafkaHelper.extractMessageId(rec)).thenReturn("m1");
            when(idempotencyService.isDuplicate("m1")).thenReturn(false);
            when(recipientResolver.resolveLandlordAndManager(houseId))
                    .thenReturn(List.of(landlordId, managerId));
            when(userGrpcClient.getUserById(staffId)).thenReturn(
                    UserResponse.newBuilder().setId(staffId.toString()).setName("Staff B").build());

            IssueQuoteSubmittedEvent event = new IssueQuoteSubmittedEvent(
                    "m1", issueId, quoteId, houseId, staffId, BigDecimal.valueOf(550_000), null);
            when(objectMapper.readValue("v", IssueQuoteSubmittedEvent.class)).thenReturn(event);

            listener.handleIssueQuoteSubmitted(rec, ack);

            ArgumentCaptor<Map> metadataCap = ArgumentCaptor.forClass(Map.class);
            verify(notificationService, times(2)).send(
                    any(UUID.class),
                    eq(NotificationCategory.ISSUE_QUOTE_WAITING_MANAGER_APPROVAL),
                    any(),
                    any(),
                    eq("/issues/" + issueId),
                    metadataCap.capture());
            assertThat(metadataCap.getValue())
                    .containsEntry("issueId", issueId.toString())
                    .containsEntry("quoteId", quoteId.toString())
                    .containsEntry("houseId", houseId.toString())
                    .containsEntry("staffId", staffId.toString())
                    .containsEntry("status", "WAITING_MANAGER_APPROVAL_QUOTE")
                    .containsEntry("totalPrice", "550000");
            verify(ack).acknowledge();
        }
    }
}
