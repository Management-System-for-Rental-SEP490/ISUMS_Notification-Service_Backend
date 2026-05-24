package com.isums.notificationservice.infrastructures.listeners;

import com.isums.notificationservice.domains.enums.LocaleType;
import com.isums.notificationservice.infrastructures.abstracts.EmailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("EContractEventListener")
class EContractEventListenerTest {

    @Mock private EmailService emailService;

    @InjectMocks private EContractEventListener listener;

    @Test
    @DisplayName("extracts recipientEmail + sends econtract_view_confirm on happy path")
    void happy() {
        String payload = """
                {"recipientEmail":"alice@example.com","recipientName":"Alice",\
                "url":"https://view.example/pdf","confirmUrl":"https://confirm.example",\
                "contractName":"HD test","contractId":"abcdef1234567890"}
                """;

        listener.handleConfirmAndSendToTenant(payload);

        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
        verify(emailService).sendEmail(eq("alice@example.com"), eq("econtract_view_confirm"),
                eq(LocaleType.vi_VN), cap.capture());
        Map<String, Object> vars = cap.getValue();
        assertThat(vars)
                .containsEntry("tenantName", "Alice")
                .containsEntry("contractName", "HD test")
                .containsEntry("contractNo", "ABCDEF12")
                .containsEntry("viewUrl", "https://view.example/pdf")
                .containsEntry("confirmUrl", "https://confirm.example");
    }

    @Test
    @DisplayName("skips quietly when payload missing recipientEmail")
    void noEmailSkips() {
        String payload = "{\"url\":\"https://view.example/pdf\",\"contractName\":\"HD\"}";

        listener.handleConfirmAndSendToTenant(payload);

        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("skips quietly on null payload")
    void nullPayload() {
        listener.handleConfirmAndSendToTenant(null);
        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("falls back to confirmUrl=url when confirmUrl missing")
    void confirmUrlFallsBackToUrl() {
        String payload = """
                {"recipientEmail":"alice@example.com","url":"https://view.example/pdf"}
                """;

        listener.handleConfirmAndSendToTenant(payload);

        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
        verify(emailService).sendEmail(eq("alice@example.com"), eq("econtract_view_confirm"),
                eq(LocaleType.vi_VN), cap.capture());
        assertThat(cap.getValue())
                .containsEntry("viewUrl", "https://view.example/pdf")
                .containsEntry("confirmUrl", "https://view.example/pdf");
    }

    @Test
    @DisplayName("uses default tenantName='bạn' when recipientName missing")
    void defaultTenantName() {
        String payload = """
                {"recipientEmail":"x@y.com","url":"u","contractId":"12345678abcdef"}
                """;

        listener.handleConfirmAndSendToTenant(payload);

        ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
        verify(emailService).sendEmail(eq("x@y.com"), eq("econtract_view_confirm"),
                eq(LocaleType.vi_VN), cap.capture());
        assertThat(cap.getValue()).containsEntry("tenantName", "bạn");
    }

    @Test
    @DisplayName("swallows EmailService failure (Throwable catch — no Kafka retry, this listener is fire-and-log)")
    void swallowsDownstreamFailure() {
        String payload = """
                {"recipientEmail":"a@b.com","url":"u"}
                """;
        doThrow(new RuntimeException("smtp")).when(emailService)
                .sendEmail(any(), any(), any(), any());

        listener.handleConfirmAndSendToTenant(payload);
    }
}
