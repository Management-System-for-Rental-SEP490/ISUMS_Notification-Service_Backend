package com.isums.notificationservice.infrastructures.kafka;

import com.isums.notificationservice.domains.enums.NotificationCategory;
import com.isums.notificationservice.domains.events.InspectionDoneNotifyEvent;
import com.isums.notificationservice.domains.events.InspectionScheduledEvent;
import com.isums.notificationservice.infrastructures.abstracts.ManagerNotificationService;
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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContractEventListener (notification-service)")
class ContractEventListenerTest {

    @Mock private ManagerNotificationService notificationService;
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
            InspectionScheduledEvent event = new InspectionScheduledEvent(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Alice", "m1");
            when(objectMapper.readValue("v", InspectionScheduledEvent.class)).thenReturn(event);

            listener.handleInspectionScheduled(rec, ack);

            ArgumentCaptor<NotificationCategory> cap = ArgumentCaptor.forClass(NotificationCategory.class);
            verify(notificationService).send(eq(event.getManagerId()), cap.capture(),
                    anyString(), anyString(), anyString(), any(Map.class));
            assertThat(cap.getValue()).isEqualTo(NotificationCategory.CONTRACT_EXPIRED);
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
}
