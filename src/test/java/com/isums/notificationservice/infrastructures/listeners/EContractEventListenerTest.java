package com.isums.notificationservice.infrastructures.listeners;

import com.isums.notificationservice.domains.enums.LocaleType;
import com.isums.notificationservice.domains.events.ConfirmAndSendToTenantEvent;
import com.isums.notificationservice.domains.events.RenewalReminderEvent;
import com.isums.notificationservice.infrastructures.abstracts.EmailService;
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
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EContractEventListener")
class EContractEventListenerTest {

    @Mock private EmailService emailService;
    @Mock private UserGrpcClient userGrpcClient;
    @Mock private IdempotencyService idempotencyService;
    @Mock private KafkaListenerHelper kafkaHelper;
    @Mock private ObjectMapper objectMapper;
    @Mock private Acknowledgment ack;

    @InjectMocks private EContractEventListener listener;

    @Nested
    @DisplayName("handleConfirmAndSendToTenant")
    class Confirm {

        private ConsumerRecord<String, String> rec =
                new ConsumerRecord<>("confirmAndSendToTenant-topic", 0, 0L, "k", "v");

        private ConfirmAndSendToTenantEvent event() {
            return ConfirmAndSendToTenantEvent.builder()
                    .messageId("m1")
                    .recipientUserId(UUID.randomUUID())
                    .contractId(UUID.randomUUID())
                    .contractName("HD")
                    .url("https://view.example/pdf")
                    .confirmUrl("https://confirm.example")
                    .startDate(Instant.now())
                    .endDate(Instant.now().plusSeconds(86400 * 30))
                    .build();
        }

        @Test
        @DisplayName("sends econtract_view_confirm email on happy path")
        void happy() throws Exception {
            when(kafkaHelper.extractMessageId(rec)).thenReturn("m1");
            ConfirmAndSendToTenantEvent event = event();
            when(objectMapper.readValue("v", ConfirmAndSendToTenantEvent.class)).thenReturn(event);
            when(idempotencyService.isDuplicate("m1")).thenReturn(false);
            UserResponse user = UserResponse.newBuilder()
                    .setId(event.getRecipientUserId().toString())
                    .setEmail("alice@example.com").setName("Alice").build();
            when(userGrpcClient.getUserById(event.getRecipientUserId())).thenReturn(user);

            listener.handleConfirmAndSendToTenant(rec, ack);

            verify(emailService).sendEmail(eq("alice@example.com"), eq("econtract_view_confirm"),
                    eq(LocaleType.vi_VN), any());
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("acks on JacksonException (poison pill — bug fix: deserialize is now inside try)")
        void jackson() throws Exception {
            when(kafkaHelper.extractMessageId(rec)).thenReturn("m1");
            when(objectMapper.readValue(any(String.class), eq(ConfirmAndSendToTenantEvent.class)))
                    .thenThrow(new JacksonException("bad") {});

            listener.handleConfirmAndSendToTenant(rec, ack);

            verify(ack).acknowledge();
            verifyNoInteractions(emailService);
        }

        @Test
        @DisplayName("skips-and-acks when duplicate")
        void duplicate() throws Exception {
            when(kafkaHelper.extractMessageId(rec)).thenReturn("m1");
            when(objectMapper.readValue("v", ConfirmAndSendToTenantEvent.class)).thenReturn(event());
            when(idempotencyService.isDuplicate("m1")).thenReturn(true);

            listener.handleConfirmAndSendToTenant(rec, ack);

            verify(ack).acknowledge();
            verifyNoInteractions(userGrpcClient, emailService);
        }

        @Test
        @DisplayName("skips when recipientUserId null")
        void noRecipient() throws Exception {
            when(kafkaHelper.extractMessageId(rec)).thenReturn("m1");
            ConfirmAndSendToTenantEvent e = event();
            e.setRecipientUserId(null);
            when(objectMapper.readValue("v", ConfirmAndSendToTenantEvent.class)).thenReturn(e);
            when(idempotencyService.isDuplicate(any())).thenReturn(false);

            listener.handleConfirmAndSendToTenant(rec, ack);

            verify(ack).acknowledge();
            verifyNoInteractions(userGrpcClient, emailService);
        }

        @Test
        @DisplayName("skips when url blank")
        void noUrl() throws Exception {
            when(kafkaHelper.extractMessageId(rec)).thenReturn("m1");
            ConfirmAndSendToTenantEvent e = event();
            e.setUrl("");
            when(objectMapper.readValue("v", ConfirmAndSendToTenantEvent.class)).thenReturn(e);
            when(idempotencyService.isDuplicate(any())).thenReturn(false);

            listener.handleConfirmAndSendToTenant(rec, ack);

            verify(ack).acknowledge();
            verifyNoInteractions(userGrpcClient, emailService);
        }

        @Test
        @DisplayName("skips when gRPC returns null user")
        void userNull() throws Exception {
            when(kafkaHelper.extractMessageId(rec)).thenReturn("m1");
            ConfirmAndSendToTenantEvent e = event();
            when(objectMapper.readValue("v", ConfirmAndSendToTenantEvent.class)).thenReturn(e);
            when(idempotencyService.isDuplicate(any())).thenReturn(false);
            when(userGrpcClient.getUserById(any())).thenReturn(null);

            listener.handleConfirmAndSendToTenant(rec, ack);

            verify(ack).acknowledge();
            verifyNoInteractions(emailService);
        }

        @Test
        @DisplayName("rethrows RuntimeException for retry on unexpected error")
        void retry() throws Exception {
            when(kafkaHelper.extractMessageId(rec)).thenReturn("m1");
            ConfirmAndSendToTenantEvent e = event();
            when(objectMapper.readValue("v", ConfirmAndSendToTenantEvent.class)).thenReturn(e);
            when(idempotencyService.isDuplicate(any())).thenReturn(false);
            when(userGrpcClient.getUserById(any())).thenThrow(new RuntimeException("grpc"));

            assertThatThrownBy(() -> listener.handleConfirmAndSendToTenant(rec, ack))
                    .isInstanceOf(RuntimeException.class);
            verify(ack, never()).acknowledge();
        }
    }

    @Nested
    @DisplayName("handleRenewalReminder")
    class Renewal {

        private ConsumerRecord<String, String> rec =
                new ConsumerRecord<>("contract.renewal.reminder", 0, 0L, "k", "v");

        @Test
        @DisplayName("sends contract_renewal_reminder email on happy path")
        void happy() throws Exception {
            when(kafkaHelper.extractMessageId(rec)).thenReturn("m1");
            when(idempotencyService.isDuplicate("m1")).thenReturn(false);
            RenewalReminderEvent event = new RenewalReminderEvent(
                    UUID.randomUUID(), UUID.randomUUID(), 14,
                    Instant.now().plusSeconds(86400 * 14), "m1");
            when(objectMapper.readValue("v", RenewalReminderEvent.class)).thenReturn(event);
            UserResponse user = UserResponse.newBuilder()
                    .setId(event.getTenantId().toString())
                    .setEmail("alice@example.com").setName("Alice").build();
            when(userGrpcClient.getUserById(event.getTenantId())).thenReturn(user);

            listener.handleRenewalReminder(rec, ack);

            verify(emailService).sendEmail(eq("alice@example.com"),
                    eq("contract_renewal_reminder"), eq(LocaleType.vi_VN), any());
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("rethrows for retry on any failure")
        void retry() throws Exception {
            when(kafkaHelper.extractMessageId(rec)).thenReturn("m1");
            when(idempotencyService.isDuplicate("m1")).thenReturn(false);
            when(objectMapper.readValue(any(String.class), eq(RenewalReminderEvent.class)))
                    .thenThrow(new RuntimeException("bad"));

            assertThatThrownBy(() -> listener.handleRenewalReminder(rec, ack))
                    .isInstanceOf(RuntimeException.class);
            verify(ack, never()).acknowledge();
        }
    }
}
