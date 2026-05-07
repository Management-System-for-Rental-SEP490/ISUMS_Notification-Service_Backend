package com.isums.notificationservice.infrastructures.listeners;

import com.isums.notificationservice.domains.enums.LocaleType;
import com.isums.notificationservice.domains.events.DepositPaidEvent;
import com.isums.notificationservice.infrastructures.abstracts.EmailService;
import com.isums.notificationservice.infrastructures.grpcs.UserGrpcClient;
import com.isums.userservice.grpc.UserResponse;
import common.kafkas.IdempotencyService;
import common.kafkas.KafkaListenerHelper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentEventListener (notification-service)")
class PaymentEventListenerTest {

    @Mock private EmailService emailService;
    @Mock private UserGrpcClient userGrpcClient;
    @Mock private IdempotencyService idempotencyService;
    @Mock private KafkaListenerHelper kafkaHelper;
    @Mock private ObjectMapper objectMapper;
    @Mock private Acknowledgment ack;

    @InjectMocks private PaymentEventListener listener;

    private final ConsumerRecord<String, String> rec =
            new ConsumerRecord<>("payment-paid-topic", 0, 0L, "k", "v");

    private DepositPaidEvent event(String type) {
        UUID id = UUID.randomUUID();
        return DepositPaidEvent.builder()
                .invoiceId(UUID.randomUUID()).contractId(UUID.randomUUID())
                .tenantId(id).houseId(UUID.randomUUID())
                .amount(5_000_000L).invoiceType(type).txnNo("TXN1")
                .paidAt(Instant.now()).build();
    }

    @Test
    @DisplayName("sends payment_receipt email on happy path")
    void happy() throws Exception {
        when(kafkaHelper.extractMessageId(rec)).thenReturn("m1");
        when(idempotencyService.isDuplicate("m1")).thenReturn(false);
        DepositPaidEvent evt = event("MONTHLY_RENT");
        when(objectMapper.readValue("v", DepositPaidEvent.class)).thenReturn(evt);
        UserResponse user = UserResponse.newBuilder()
                .setId(evt.tenantId().toString()).setEmail("alice@example.com").setName("Alice").build();
        when(userGrpcClient.getUserById(evt.tenantId())).thenReturn(user);

        listener.handlePaymentPaid(rec, ack);

        verify(emailService).sendEmail(eq("alice@example.com"), eq("payment_receipt"),
                eq(LocaleType.vi_VN), any());
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("skips when duplicate")
    void duplicate() {
        when(kafkaHelper.extractMessageId(rec)).thenReturn("m1");
        when(idempotencyService.isDuplicate("m1")).thenReturn(true);

        listener.handlePaymentPaid(rec, ack);

        verify(ack).acknowledge();
        verifyNoInteractions(emailService, userGrpcClient);
    }

    @Test
    @DisplayName("acks and skips when gRPC returns null user")
    void userNull() throws Exception {
        when(kafkaHelper.extractMessageId(rec)).thenReturn("m1");
        when(idempotencyService.isDuplicate("m1")).thenReturn(false);
        DepositPaidEvent evt = event("DEPOSIT");
        when(objectMapper.readValue("v", DepositPaidEvent.class)).thenReturn(evt);
        when(userGrpcClient.getUserById(any())).thenReturn(null);

        listener.handlePaymentPaid(rec, ack);

        verify(ack).acknowledge();
        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("acks on JacksonException (poison pill)")
    void jackson() throws Exception {
        when(kafkaHelper.extractMessageId(rec)).thenReturn("m1");
        when(idempotencyService.isDuplicate("m1")).thenReturn(false);
        when(objectMapper.readValue(any(String.class), eq(DepositPaidEvent.class)))
                .thenThrow(new JacksonException("bad") {});

        listener.handlePaymentPaid(rec, ack);

        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("rethrows for retry on email send failure")
    void retry() throws Exception {
        when(kafkaHelper.extractMessageId(rec)).thenReturn("m1");
        when(idempotencyService.isDuplicate("m1")).thenReturn(false);
        DepositPaidEvent evt = event("UTILITY");
        when(objectMapper.readValue("v", DepositPaidEvent.class)).thenReturn(evt);
        UserResponse user = UserResponse.newBuilder()
                .setId(evt.tenantId().toString()).setEmail("a@b.com").build();
        when(userGrpcClient.getUserById(any())).thenReturn(user);
        doThrow(new RuntimeException("smtp")).when(emailService).sendEmail(any(), any(), any(), any());

        assertThatThrownBy(() -> listener.handlePaymentPaid(rec, ack))
                .isInstanceOf(RuntimeException.class);
        verify(ack, never()).acknowledge();
    }
}
