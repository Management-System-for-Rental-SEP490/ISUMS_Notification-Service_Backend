package com.isums.notificationservice.infrastructures.kafka;

import com.isums.notificationservice.domains.enums.LocaleType;
import com.isums.notificationservice.domains.enums.NotificationCategory;
import com.isums.notificationservice.domains.events.OverdueTerminationRequestedEvent;
import com.isums.notificationservice.domains.events.PowerCutConfirmedEvent;
import com.isums.notificationservice.domains.events.PowerCutReviewRequestedEvent;
import com.isums.notificationservice.infrastructures.abstracts.EmailService;
import com.isums.notificationservice.infrastructures.abstracts.ManagerNotificationService;
import com.isums.notificationservice.infrastructures.grpcs.UserGrpcClient;
import com.isums.userservice.grpc.UserResponse;
import common.kafkas.IdempotencyService;
import common.kafkas.KafkaListenerHelper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentConsumer")
class PaymentConsumerTest {

    @Mock private ManagerNotificationService notificationService;
    @Mock private UserGrpcClient userGrpcClient;
    @Mock private EmailService emailService;
    @Mock private IdempotencyService idempotencyService;
    @Mock private KafkaListenerHelper kafkaHelper;
    @Mock private ObjectMapper objectMapper;
    @Mock private Acknowledgment ack;

    @InjectMocks private PaymentConsumer consumer;

    @Nested
    @DisplayName("handlePowerCutConfirmed")
    class PowerCutConfirmed {

        private ConsumerRecord<String, String> rec =
                new ConsumerRecord<>("contract.power-cut-confirmed", 0, 0L, "k", "v");

        @Test
        @DisplayName("sends power_cut_warning_24h email")
        void happy() throws Exception {
            when(kafkaHelper.extractMessageId(rec)).thenReturn("m1");
            when(idempotencyService.isDuplicate("m1")).thenReturn(false);
            PowerCutConfirmedEvent event = new PowerCutConfirmedEvent(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                    UUID.randomUUID(), Instant.now().plusSeconds(86400), "m1");
            when(objectMapper.readValue("v", PowerCutConfirmedEvent.class)).thenReturn(event);
            UserResponse tenant = UserResponse.newBuilder()
                    .setId(event.getTenantId().toString()).setEmail("a@b.com").build();
            when(userGrpcClient.getUserById(event.getTenantId())).thenReturn(tenant);

            consumer.handlePowerCutConfirmed(rec, ack);

            verify(emailService).sendEmail(eq("a@b.com"), eq("power_cut_warning_24h"),
                    eq(LocaleType.vi_VN), any(Map.class));
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("skips when duplicate")
        void duplicate() {
            when(kafkaHelper.extractMessageId(rec)).thenReturn("m1");
            when(idempotencyService.isDuplicate("m1")).thenReturn(true);

            consumer.handlePowerCutConfirmed(rec, ack);

            verify(ack).acknowledge();
            verifyNoInteractions(emailService);
        }

        @Test
        @DisplayName("rethrows for retry on failure")
        void retry() throws Exception {
            when(kafkaHelper.extractMessageId(rec)).thenReturn("m1");
            when(idempotencyService.isDuplicate("m1")).thenReturn(false);
            when(objectMapper.readValue(any(String.class), eq(PowerCutConfirmedEvent.class)))
                    .thenThrow(new RuntimeException("bad"));

            assertThatThrownBy(() -> consumer.handlePowerCutConfirmed(rec, ack))
                    .isInstanceOf(RuntimeException.class);
            verify(ack, never()).acknowledge();
        }
    }

    @Nested
    @DisplayName("handlePowerCutReviewRequested")
    class ReviewRequested {

        private ConsumerRecord<String, String> rec =
                new ConsumerRecord<>("contract.power-cut-review-requested", 0, 0L, "k", "v");

        @Test
        @DisplayName("sends manager PAYMENT_OVERDUE notification")
        void happy() throws Exception {
            when(kafkaHelper.extractMessageId(rec)).thenReturn("m1");
            when(idempotencyService.isDuplicate("m1")).thenReturn(false);
            PowerCutReviewRequestedEvent event = PowerCutReviewRequestedEvent.builder()
                    .contractId(UUID.randomUUID()).houseId(UUID.randomUUID())
                    .managerId(UUID.randomUUID()).tenantName("Alice")
                    .daysLate(15).totalAmount(5_000_000L).messageId("m1").build();
            when(objectMapper.readValue("v", PowerCutReviewRequestedEvent.class)).thenReturn(event);

            consumer.handlePowerCutReviewRequested(rec, ack);

            verify(notificationService).send(eq(event.getManagerId()),
                    eq(NotificationCategory.PAYMENT_OVERDUE),
                    anyString(), anyString(), anyString(), any(Map.class));
            verify(ack).acknowledge();
        }
    }

    @Nested
    @DisplayName("handleOverdueTerminationRequested")
    class Overdue {

        private ConsumerRecord<String, String> rec =
                new ConsumerRecord<>("contract.termination-overdue-requested", 0, 0L, "k", "v");

        @Test
        @DisplayName("sends manager PAYMENT_OVERDUE notification")
        void happy() throws Exception {
            when(kafkaHelper.extractMessageId(rec)).thenReturn("m1");
            when(idempotencyService.isDuplicate("m1")).thenReturn(false);
            OverdueTerminationRequestedEvent event = new OverdueTerminationRequestedEvent(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Bob", "m1");
            when(objectMapper.readValue("v", OverdueTerminationRequestedEvent.class)).thenReturn(event);

            consumer.handleOverdueTerminationRequested(rec, ack);

            verify(notificationService).send(eq(event.getManagerId()),
                    eq(NotificationCategory.PAYMENT_OVERDUE),
                    anyString(), anyString(), anyString(), any(Map.class));
            verify(ack).acknowledge();
        }
    }
}
