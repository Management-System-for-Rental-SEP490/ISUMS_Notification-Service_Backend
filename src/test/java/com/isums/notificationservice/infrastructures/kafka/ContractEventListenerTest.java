package com.isums.notificationservice.infrastructures.kafka;

import com.isums.notificationservice.domains.enums.NotificationCategory;
import com.isums.notificationservice.domains.events.ContractCancelledByTenantEvent;
import com.isums.notificationservice.domains.events.ContractCompletedEvent;
import com.isums.notificationservice.domains.events.ContractReadyForLandlordSignatureEvent;
import com.isums.notificationservice.domains.events.InspectionDoneNotifyEvent;
import com.isums.notificationservice.domains.events.InspectionScheduledEvent;
import com.isums.notificationservice.infrastructures.abstracts.ManagerNotificationService;
import com.isums.notificationservice.services.NotificationRecipientResolver;
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

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContractEventListener (notification-service)")
class ContractEventListenerTest {

    @Mock private ManagerNotificationService notificationService;
    @Mock private NotificationRecipientResolver recipientResolver;
    @Mock private ObjectMapper objectMapper;
    @Mock private IdempotencyService idempotencyService;
    @Mock private KafkaListenerHelper kafkaHelper;
    @Mock private Acknowledgment ack;

    @InjectMocks private ContractEventListener listener;

    @Nested
    @DisplayName("handleInspectionScheduled")
    class Scheduled {

        private ConsumerRecord<String, String> rec =
                new ConsumerRecord<>("contract.inspection.scheduled", 0, 0L, "k", "v");

        @Test
        @DisplayName("sends CONTRACT_EXPIRED notification on happy path")
        void happy() throws Exception {
            when(kafkaHelper.extractMessageId(rec)).thenReturn("m1");
            when(idempotencyService.isDuplicate("m1")).thenReturn(false);
            UUID houseId = UUID.randomUUID();
            UUID managerId = UUID.randomUUID();
            UUID landlordId = UUID.randomUUID();
            InspectionScheduledEvent event = new InspectionScheduledEvent(
                    UUID.randomUUID(), UUID.randomUUID(), houseId, managerId, "Alice", "m1");
            when(objectMapper.readValue("v", InspectionScheduledEvent.class)).thenReturn(event);
            when(recipientResolver.resolveLandlordAndManager(houseId, managerId))
                    .thenReturn(List.of(landlordId, managerId));

            listener.handleInspectionScheduled(rec, ack);

            ArgumentCaptor<NotificationCategory> cap = ArgumentCaptor.forClass(NotificationCategory.class);
            verify(notificationService, times(2)).send(any(UUID.class), cap.capture(),
                    anyString(), anyString(), anyString(), any(Map.class));
            assertThat(cap.getAllValues()).containsOnly(NotificationCategory.CONTRACT_EXPIRED);
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("skips-and-acks when duplicate")
        void duplicate() {
            when(kafkaHelper.extractMessageId(rec)).thenReturn("m1");
            when(idempotencyService.isDuplicate("m1")).thenReturn(true);

            listener.handleInspectionScheduled(rec, ack);

            verify(ack).acknowledge();
            verifyNoInteractions(notificationService);
        }

        @Test
        @DisplayName("rethrows for retry on failure")
        void retry() throws Exception {
            when(kafkaHelper.extractMessageId(rec)).thenReturn("m1");
            when(idempotencyService.isDuplicate("m1")).thenReturn(false);
            when(objectMapper.readValue(any(String.class), eq(InspectionScheduledEvent.class)))
                    .thenThrow(new RuntimeException("bad"));

            assertThatThrownBy(() -> listener.handleInspectionScheduled(rec, ack))
                    .isInstanceOf(RuntimeException.class);
            verify(ack, never()).acknowledge();
        }
    }

    @Nested
    @DisplayName("handleInspectionDone")
    class Done {

        private ConsumerRecord<String, String> rec =
                new ConsumerRecord<>("contract.inspection.done", 0, 0L, "k", "v");

        @Test
        @DisplayName("sends INSPECTION_DONE notification on happy path")
        void happy() throws Exception {
            when(kafkaHelper.extractMessageId(rec)).thenReturn("m1");
            when(idempotencyService.isDuplicate("m1")).thenReturn(false);
            InspectionDoneNotifyEvent event = new InspectionDoneNotifyEvent(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 100_000L, "m1");
            when(objectMapper.readValue("v", InspectionDoneNotifyEvent.class)).thenReturn(event);

            listener.handleInspectionDone(rec, ack);

            verify(notificationService).send(eq(event.getManagerId()),
                    eq(NotificationCategory.INSPECTION_DONE),
                    anyString(), anyString(), anyString(), any(Map.class));
            verify(ack).acknowledge();
        }
    }

    @Nested
    @DisplayName("handleReadyForLandlordSignature")
    class ReadyForLandlordSignature {

        private final ConsumerRecord<String, String> rec =
                new ConsumerRecord<>("contract.ready-for-landlord-signature", 0, 0L, "k", "v");

        @Test
        @DisplayName("sends CONTRACT_READY_FOR_LANDLORD_SIGNATURE notification on happy path")
        void happy() throws Exception {
            when(kafkaHelper.extractMessageId(rec)).thenReturn("m1");
            when(idempotencyService.isDuplicate("m1")).thenReturn(false);

            ContractReadyForLandlordSignatureEvent event = new ContractReadyForLandlordSignatureEvent(
                    "m1", UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    "Alice", "Lease April", "doc-123");
            when(objectMapper.readValue("v", ContractReadyForLandlordSignatureEvent.class)).thenReturn(event);

            listener.handleReadyForLandlordSignature(rec, ack);

            ArgumentCaptor<Map> metadataCap = ArgumentCaptor.forClass(Map.class);
            verify(notificationService).send(
                    eq(event.getRecipientUserId()),
                    eq(NotificationCategory.CONTRACT_READY_FOR_LANDLORD_SIGNATURE),
                    anyString(),
                    anyString(),
                    eq("/contracts/" + event.getContractId()),
                    metadataCap.capture()
            );
            assertThat(metadataCap.getValue())
                    .containsEntry("contractId", event.getContractId().toString())
                    .containsEntry("tenantId", event.getTenantId().toString())
                    .containsEntry("documentId", "doc-123")
                    .containsEntry("status", "READY");
            verify(ack).acknowledge();
        }
    }

    @Nested
    @DisplayName("handleContractCompleted")
    class ContractCompleted {

        private final ConsumerRecord<String, String> rec =
                new ConsumerRecord<>("contract-completed-topic", 0, 0L, "k", "v");

        @Test
        @DisplayName("sends CONTRACT_COMPLETED notification on happy path")
        void happy() throws Exception {
            when(kafkaHelper.extractMessageId(rec)).thenReturn("m1");
            when(idempotencyService.isDuplicate("m1")).thenReturn(false);

            UUID contractId = UUID.randomUUID();
            UUID tenantId = UUID.randomUUID();
            UUID houseId = UUID.randomUUID();
            UUID landlordId = UUID.randomUUID();
            UUID managerId = UUID.randomUUID();
            Instant completedAt = Instant.now();

            ContractCompletedEvent event = new ContractCompletedEvent(
                    contractId,
                    tenantId,
                    "alice@example.com",
                    false,
                    houseId,
                    landlordId,
                    1_000_000L,
                    5_000_000L,
                    5,
                    Instant.now(),
                    Instant.now().plusSeconds(86_400),
                    completedAt,
                    "https://signed-pdf"
            );
            when(objectMapper.readValue("v", ContractCompletedEvent.class)).thenReturn(event);
            when(recipientResolver.resolveLandlordAndManager(houseId, landlordId))
                    .thenReturn(List.of(landlordId, managerId));

            listener.handleContractCompleted(rec, ack);

            ArgumentCaptor<Map> metadataCap = ArgumentCaptor.forClass(Map.class);
            verify(notificationService, times(2)).send(
                    any(UUID.class),
                    eq(NotificationCategory.CONTRACT_COMPLETED),
                    anyString(),
                    anyString(),
                    eq("/contracts/" + contractId),
                    metadataCap.capture()
            );
            assertThat(metadataCap.getValue())
                    .containsEntry("contractId", contractId.toString())
                    .containsEntry("tenantId", tenantId.toString())
                    .containsEntry("houseId", houseId.toString())
                    .containsEntry("status", "COMPLETED")
                    .containsEntry("completedAt", completedAt.toString())
                    .containsEntry("signedPdfUrl", "https://signed-pdf");
            verify(ack).acknowledge();
        }
    }

    @Nested
    @DisplayName("handleContractCancelledByTenant")
    class ContractCancelledByTenant {

        private final ConsumerRecord<String, String> rec =
                new ConsumerRecord<>("contract.cancelled-by-tenant", 0, 0L, "k", "v");

        @Test
        @DisplayName("sends CONTRACT_CANCELLED_BY_TENANT notification to landlord and manager")
        void happy() throws Exception {
            when(kafkaHelper.extractMessageId(rec)).thenReturn("m1");
            when(idempotencyService.isDuplicate("m1")).thenReturn(false);

            UUID contractId = UUID.randomUUID();
            UUID houseId = UUID.randomUUID();
            UUID tenantId = UUID.randomUUID();
            UUID initiatorId = UUID.randomUUID();
            UUID landlordId = UUID.randomUUID();
            UUID managerId = UUID.randomUUID();
            Instant cancelledAt = Instant.now();

            ContractCancelledByTenantEvent event = new ContractCancelledByTenantEvent(
                    "m1",
                    contractId,
                    houseId,
                    tenantId,
                    "Alice",
                    "Khong ky nua",
                    cancelledAt,
                    initiatorId
            );

            when(objectMapper.readValue("v", ContractCancelledByTenantEvent.class)).thenReturn(event);
            when(recipientResolver.resolveLandlordAndManager(houseId, initiatorId))
                    .thenReturn(List.of(landlordId, managerId));

            listener.handleContractCancelledByTenant(rec, ack);

            ArgumentCaptor<Map> metadataCap = ArgumentCaptor.forClass(Map.class);
            verify(notificationService, times(2)).send(
                    any(UUID.class),
                    eq(NotificationCategory.CONTRACT_CANCELLED_BY_TENANT),
                    anyString(),
                    anyString(),
                    eq("/contracts/" + contractId),
                    metadataCap.capture()
            );
            assertThat(metadataCap.getValue())
                    .containsEntry("contractId", contractId.toString())
                    .containsEntry("houseId", houseId.toString())
                    .containsEntry("tenantId", tenantId.toString())
                    .containsEntry("status", "CANCELLED_BY_TENANT")
                    .containsEntry("cancelledAt", cancelledAt.toString())
                    .containsEntry("reason", "Khong ky nua");
            verify(ack).acknowledge();
        }
    }
}
