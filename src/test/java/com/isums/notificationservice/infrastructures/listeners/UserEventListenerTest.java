package com.isums.notificationservice.infrastructures.listeners;

import com.isums.notificationservice.domains.enums.LocaleType;
import com.isums.notificationservice.domains.events.SendEmailEvent;
import com.isums.notificationservice.domains.events.UserActivatedEvent;
import com.isums.notificationservice.infrastructures.abstracts.EmailService;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserEventListener")
class UserEventListenerTest {

    @Mock private EmailService emailService;
    @Mock private IdempotencyService idempotencyService;
    @Mock private KafkaListenerHelper kafkaHelper;
    @Mock private ObjectMapper objectMapper;
    @Mock private Acknowledgment ack;

    @InjectMocks private UserEventListener listener;

    @Nested
    @DisplayName("handleSendEmail")
    class HandleSendEmail {

        private ConsumerRecord<String, String> rec = new ConsumerRecord<>("notification-email", 0, 0L, "k", "v");

        @Test
        @DisplayName("dispatches email to EmailService on happy path")
        void happy() throws Exception {
            when(kafkaHelper.extractMessageId(rec)).thenReturn("m1");
            when(idempotencyService.isDuplicate("m1")).thenReturn(false);
            SendEmailEvent event = new SendEmailEvent(
                    "alice@example.com", "WELCOME", Map.of("name", "Alice"));
            when(objectMapper.readValue("v", SendEmailEvent.class)).thenReturn(event);

            listener.handleSendEmail(rec, ack);

            verify(emailService).sendEmail("alice@example.com", "welcome",
                    LocaleType.vi_VN, Map.of("name", "Alice"));
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("skips-and-acks when duplicate")
        void duplicate() {
            when(kafkaHelper.extractMessageId(rec)).thenReturn("m1");
            when(idempotencyService.isDuplicate("m1")).thenReturn(true);

            listener.handleSendEmail(rec, ack);

            verify(ack).acknowledge();
            verifyNoInteractions(emailService);
        }

        @Test
        @DisplayName("acks and skips when 'to' is blank (invalid event)")
        void missingTo() throws Exception {
            when(kafkaHelper.extractMessageId(rec)).thenReturn("m1");
            when(idempotencyService.isDuplicate("m1")).thenReturn(false);
            when(objectMapper.readValue("v", SendEmailEvent.class))
                    .thenReturn(new SendEmailEvent(null, "x", Map.of()));

            listener.handleSendEmail(rec, ack);

            verify(ack).acknowledge();
            verifyNoInteractions(emailService);
        }

        @Test
        @DisplayName("acks on JacksonException (poison-pill handling)")
        void jacksonException() throws Exception {
            when(kafkaHelper.extractMessageId(rec)).thenReturn("m1");
            when(idempotencyService.isDuplicate("m1")).thenReturn(false);
            when(objectMapper.readValue(any(String.class), eq(SendEmailEvent.class)))
                    .thenThrow(new JacksonException("bad") {});

            listener.handleSendEmail(rec, ack);

            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("rethrows RuntimeException for retry on downstream failure")
        void retry() throws Exception {
            when(kafkaHelper.extractMessageId(rec)).thenReturn("m1");
            when(idempotencyService.isDuplicate("m1")).thenReturn(false);
            SendEmailEvent event = new SendEmailEvent("a@b.com", "WELCOME", Map.of());
            when(objectMapper.readValue("v", SendEmailEvent.class)).thenReturn(event);
            doThrow(new RuntimeException("smtp"))
                    .when(emailService).sendEmail(any(), any(), any(), any());

            assertThatThrownBy(() -> listener.handleSendEmail(rec, ack))
                    .isInstanceOf(RuntimeException.class);
            verify(ack, never()).acknowledge();
        }
    }

    @Nested
    @DisplayName("handleOnUserActivated")
    class HandleActivated {

        private ConsumerRecord<String, String> rec =
                new ConsumerRecord<>("user-activated-topic", 0, 0L, "k", "v");

        private UserActivatedEvent eventWithInvoice(String paymentUrl) {
            return UserActivatedEvent.builder()
                    .userId(UUID.randomUUID())
                    .email("bob@example.com").name("Bob").tempPassword("Temp@123")
                    .firstRentPaymentUrl(paymentUrl)
                    .firstRentAmount(5_000_000L)
                    .firstRentDueDate(Instant.now().plusSeconds(86400))
                    .build();
        }

        @Test
        @DisplayName("sends user_activated email with invoice fields when firstRentPaymentUrl present")
        void withInvoice() throws Exception {
            when(kafkaHelper.extractMessageId(rec)).thenReturn("m1");
            when(idempotencyService.isDuplicate("m1")).thenReturn(false);
            when(objectMapper.readValue("v", UserActivatedEvent.class))
                    .thenReturn(eventWithInvoice("https://pay.example/1"));

            listener.handleOnUserActivated(rec, ack);

            verify(emailService).sendEmail(eq("bob@example.com"), eq("user_activated"),
                    eq(LocaleType.vi_VN), any());
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("sends without invoice params when firstRentPaymentUrl null")
        void withoutInvoice() throws Exception {
            when(kafkaHelper.extractMessageId(rec)).thenReturn("m1");
            when(idempotencyService.isDuplicate("m1")).thenReturn(false);
            when(objectMapper.readValue("v", UserActivatedEvent.class))
                    .thenReturn(eventWithInvoice(null));

            listener.handleOnUserActivated(rec, ack);

            verify(emailService).sendEmail(eq("bob@example.com"), eq("user_activated"),
                    eq(LocaleType.vi_VN), any());
            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("acks on JacksonException")
        void jackson() throws Exception {
            when(kafkaHelper.extractMessageId(rec)).thenReturn("m1");
            when(idempotencyService.isDuplicate("m1")).thenReturn(false);
            when(objectMapper.readValue(any(String.class), eq(UserActivatedEvent.class)))
                    .thenThrow(new JacksonException("bad") {});

            listener.handleOnUserActivated(rec, ack);

            verify(ack).acknowledge();
            verifyNoInteractions(emailService);
        }
    }
}
