package com.isums.notificationservice.infrastructures.listeners;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.isums.notificationservice.domains.enums.LocaleType;
import com.isums.notificationservice.domains.events.DepositPaidEvent;
import com.isums.notificationservice.domains.events.DepositRefundPaidEvent;
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

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
        DepositPaidEvent evt = event("MONTHLY_RENT");
        when(objectMapper.readValue("v", DepositPaidEvent.class)).thenReturn(evt);
        UserResponse user = UserResponse.newBuilder()
                .setId(evt.tenantId().toString()).setEmail("alice@example.com").setName("Alice").build();
        when(userGrpcClient.getUserById(evt.tenantId())).thenReturn(user);

        listener.handlePaymentPaid("v");

        verify(emailService).sendEmail(eq("alice@example.com"), eq("payment_receipt"),
                eq(LocaleType.vi_VN), any());
    }

    @Test
    @DisplayName("swallows null payload (no work, no retry)")
    void nullPayload() {
        listener.handlePaymentPaid(null);
        verifyNoInteractions(emailService, userGrpcClient, objectMapper);
    }

    @Test
    @DisplayName("skips email when gRPC returns null user and event has no email")
    void userNull() throws Exception {
        DepositPaidEvent evt = event("DEPOSIT");
        when(objectMapper.readValue("v", DepositPaidEvent.class)).thenReturn(evt);
        when(userGrpcClient.getUserById(any())).thenReturn(null);

        listener.handlePaymentPaid("v");

        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("swallows JsonParseException (poison pill — no retry)")
    void jackson() throws Exception {
        when(objectMapper.readValue(any(String.class), eq(DepositPaidEvent.class)))
                .thenThrow(new JsonParseException(null, "bad"));

        listener.handlePaymentPaid("v");

        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("rethrows for retry on email send failure")
    void retry() throws Exception {
        DepositPaidEvent evt = event("UTILITY");
        when(objectMapper.readValue("v", DepositPaidEvent.class)).thenReturn(evt);
        UserResponse user = UserResponse.newBuilder()
                .setId(evt.tenantId().toString()).setEmail("a@b.com").build();
        when(userGrpcClient.getUserById(any())).thenReturn(user);
        doThrow(new RuntimeException("smtp")).when(emailService).sendEmail(any(), any(), any(), any());

        assertThatThrownBy(() -> listener.handlePaymentPaid("v"))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("sends deposit_refund_paid_notify email when refund is marked paid")
    void depositRefundPaid() throws Exception {
        ConsumerRecord<String, String> refundRec =
                new ConsumerRecord<>("deposit-refund-paid-topic", 0, 0L, "k", "v");
        when(kafkaHelper.extractMessageId(refundRec)).thenReturn("m2");
        when(idempotencyService.isDuplicate("m2")).thenReturn(false);
        DepositRefundPaidEvent evt = DepositRefundPaidEvent.builder()
                .contractId(UUID.randomUUID())
                .tenantId(UUID.randomUUID())
                .tenantEmail("alice@example.com")
                .refundAmount(2_000_000L)
                .paymentMethod("BANK_TRANSFER")
                .note("done")
                .paidAt(Instant.now())
                .messageId("m2")
                .build();
        when(objectMapper.readValue("v", DepositRefundPaidEvent.class)).thenReturn(evt);

        listener.handleDepositRefundPaid(refundRec, ack);

        verify(emailService).sendEmail(eq("alice@example.com"), eq("deposit_refund_paid_notify"),
                eq(LocaleType.vi_VN), any());
        verify(ack).acknowledge();
    }
}
