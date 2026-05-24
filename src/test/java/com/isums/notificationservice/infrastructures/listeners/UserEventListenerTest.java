package com.isums.notificationservice.infrastructures.listeners;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.isums.notificationservice.domains.enums.LocaleType;
import com.isums.notificationservice.domains.events.SendEmailEvent;
import com.isums.notificationservice.domains.events.UserActivatedEvent;
import com.isums.notificationservice.infrastructures.abstracts.EmailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserEventListener")
class UserEventListenerTest {

    @Mock private EmailService emailService;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private UserEventListener listener;

    @Nested
    @DisplayName("handleSendEmail")
    class HandleSendEmail {

        @Test
        @DisplayName("dispatches email to EmailService on happy path (lowercased template)")
        void happy() throws Exception {
            SendEmailEvent event = SendEmailEvent.builder()
                    .to("alice@example.com")
                    .templateCode("WELCOME")
                    .params(Map.of("name", "Alice"))
                    .build();
            when(objectMapper.readValue("v", SendEmailEvent.class)).thenReturn(event);

            listener.handleSendEmail("v");

            verify(emailService).sendEmail("alice@example.com", "welcome",
                    LocaleType.vi_VN, Map.of("name", "Alice"));
        }

        @Test
        @DisplayName("swallows null payload (no work, no retry)")
        void nullPayload() {
            listener.handleSendEmail(null);
            verifyNoInteractions(emailService, objectMapper);
        }

        @Test
        @DisplayName("skips invalid event with blank 'to' field")
        void missingTo() throws Exception {
            when(objectMapper.readValue("v", SendEmailEvent.class))
                    .thenReturn(SendEmailEvent.builder().to(null).templateCode("x").params(Map.of()).build());

            listener.handleSendEmail("v");

            verifyNoInteractions(emailService);
        }

        @Test
        @DisplayName("swallows poison message on JacksonException (no retry)")
        void jacksonExceptionSkips() throws Exception {
            when(objectMapper.readValue(any(String.class), eq(SendEmailEvent.class)))
                    .thenThrow(new JsonParseException(null, "bad"));

            listener.handleSendEmail("v");

            verifyNoInteractions(emailService);
        }

        @Test
        @DisplayName("rethrows RuntimeException for retry on downstream EmailService failure")
        void downstreamFailureRetries() throws Exception {
            SendEmailEvent event = SendEmailEvent.builder()
                    .to("a@b.com").templateCode("WELCOME").params(Map.of()).build();
            when(objectMapper.readValue("v", SendEmailEvent.class)).thenReturn(event);
            doThrow(new RuntimeException("smtp"))
                    .when(emailService).sendEmail(any(), any(), any(), any());

            assertThatThrownBy(() -> listener.handleSendEmail("v"))
                    .isInstanceOf(RuntimeException.class);
        }

        @Test
        @DisplayName("forwards empty Map when params is null")
        void nullParamsFallsBackToEmptyMap() throws Exception {
            SendEmailEvent event = SendEmailEvent.builder()
                    .to("a@b.com").templateCode("WELCOME").params(null).build();
            when(objectMapper.readValue("v", SendEmailEvent.class)).thenReturn(event);

            listener.handleSendEmail("v");

            verify(emailService).sendEmail("a@b.com", "welcome", LocaleType.vi_VN, Map.of());
        }
    }

    @Nested
    @DisplayName("handleOnUserActivated")
    class HandleActivated {

        private UserActivatedEvent eventBuilder(String locale, String paymentUrl) {
            return UserActivatedEvent.builder()
                    .userId(UUID.randomUUID())
                    .email("bob@example.com")
                    .name("Bob")
                    .password("Tmp@123")
                    .locale(locale)
                    .firstRentPaymentUrl(paymentUrl)
                    .firstRentAmount(5_000_000L)
                    .firstRentDueDate(Instant.parse("2026-06-01T00:00:00Z"))
                    .build();
        }

        @Test
        @DisplayName("sends user_activated email with full invoice block when firstRentPaymentUrl present")
        void withInvoice() throws Exception {
            UserActivatedEvent event = eventBuilder("vi_VN", "https://pay.example/1");
            when(objectMapper.readValue("v", UserActivatedEvent.class)).thenReturn(event);

            listener.handleOnUserActivated("v");

            ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
            verify(emailService).sendEmail(eq("bob@example.com"), eq("user_activated"),
                    eq(LocaleType.vi_VN), cap.capture());
            Map<String, Object> p = cap.getValue();
            assertThat(p)
                    .containsEntry("name", "Bob")
                    .containsEntry("email", "bob@example.com")
                    .containsEntry("password", "Tmp@123")
                    .containsEntry("hasInvoice", true)
                    .containsEntry("invoiceTypeCode", "MONTHLY_RENT")
                    .containsEntry("invoicePaymentUrl", "https://pay.example/1")
                    .containsKey("invoiceAmount")
                    .containsKey("invoiceDueDate");
        }

        @Test
        @DisplayName("sends email without invoice fields when firstRentPaymentUrl null")
        void withoutInvoice() throws Exception {
            UserActivatedEvent event = eventBuilder("vi_VN", null);
            when(objectMapper.readValue("v", UserActivatedEvent.class)).thenReturn(event);

            listener.handleOnUserActivated("v");

            ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
            verify(emailService).sendEmail(eq("bob@example.com"), eq("user_activated"),
                    eq(LocaleType.vi_VN), cap.capture());
            Map<String, Object> p = cap.getValue();
            assertThat(p)
                    .containsEntry("hasInvoice", false)
                    .doesNotContainKey("invoicePaymentUrl")
                    .doesNotContainKey("invoiceAmount");
        }

        @Test
        @DisplayName("password is empty string when event.password is null (template-safe)")
        void nullPasswordBecomesEmpty() throws Exception {
            UserActivatedEvent event = UserActivatedEvent.builder()
                    .userId(UUID.randomUUID())
                    .email("nopass@example.com")
                    .name("Pass")
                    .password(null)
                    .locale("vi_VN")
                    .build();
            when(objectMapper.readValue("v", UserActivatedEvent.class)).thenReturn(event);

            listener.handleOnUserActivated("v");

            ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
            verify(emailService).sendEmail(eq("nopass@example.com"), eq("user_activated"),
                    eq(LocaleType.vi_VN), cap.capture());
            assertThat(cap.getValue()).containsEntry("password", "");
        }

        @Test
        @DisplayName("name falls back to email when event.name is null")
        void nullNameFallsBackToEmail() throws Exception {
            UserActivatedEvent event = UserActivatedEvent.builder()
                    .userId(UUID.randomUUID())
                    .email("noname@example.com")
                    .name(null)
                    .password("Tmp@123")
                    .locale("vi_VN")
                    .build();
            when(objectMapper.readValue("v", UserActivatedEvent.class)).thenReturn(event);

            listener.handleOnUserActivated("v");

            ArgumentCaptor<Map<String, Object>> cap = ArgumentCaptor.forClass(Map.class);
            verify(emailService).sendEmail(eq("noname@example.com"), eq("user_activated"),
                    eq(LocaleType.vi_VN), cap.capture());
            assertThat(cap.getValue()).containsEntry("name", "noname@example.com");
        }

        @Test
        @DisplayName("uses en_US locale when event.locale is en_US")
        void englishLocale() throws Exception {
            UserActivatedEvent event = eventBuilder("en_US", "https://pay.example/1");
            when(objectMapper.readValue("v", UserActivatedEvent.class)).thenReturn(event);

            listener.handleOnUserActivated("v");

            verify(emailService).sendEmail(eq("bob@example.com"), eq("user_activated"),
                    eq(LocaleType.en_US), any());
        }

        @Test
        @DisplayName("uses ja_JP locale when event.locale is ja_JP")
        void japaneseLocale() throws Exception {
            UserActivatedEvent event = eventBuilder("ja_JP", "https://pay.example/1");
            when(objectMapper.readValue("v", UserActivatedEvent.class)).thenReturn(event);

            listener.handleOnUserActivated("v");

            verify(emailService).sendEmail(eq("bob@example.com"), eq("user_activated"),
                    eq(LocaleType.ja_JP), any());
        }

        @Test
        @DisplayName("falls back to vi_VN when event.locale is null")
        void nullLocaleFallsBackToViVn() throws Exception {
            UserActivatedEvent event = eventBuilder(null, "https://pay.example/1");
            when(objectMapper.readValue("v", UserActivatedEvent.class)).thenReturn(event);

            listener.handleOnUserActivated("v");

            verify(emailService).sendEmail(eq("bob@example.com"), eq("user_activated"),
                    eq(LocaleType.vi_VN), any());
        }

        @Test
        @DisplayName("falls back to vi_VN when event.locale is garbage")
        void invalidLocaleFallsBackToViVn() throws Exception {
            UserActivatedEvent event = eventBuilder("xx_YY", "https://pay.example/1");
            when(objectMapper.readValue("v", UserActivatedEvent.class)).thenReturn(event);

            listener.handleOnUserActivated("v");

            verify(emailService).sendEmail(eq("bob@example.com"), eq("user_activated"),
                    eq(LocaleType.vi_VN), any());
        }

        @Test
        @DisplayName("swallows poison message on JacksonException (no retry, no DLQ — preserve original semantics)")
        void jacksonExceptionSkips() throws Exception {
            when(objectMapper.readValue(any(String.class), eq(UserActivatedEvent.class)))
                    .thenThrow(new JsonParseException(null, "bad"));

            listener.handleOnUserActivated("v");

            verifyNoInteractions(emailService);
        }

        @Test
        @DisplayName("swallows null payload (no work, no retry)")
        void nullPayload() {
            listener.handleOnUserActivated(null);
            verifyNoInteractions(emailService, objectMapper);
        }

        @Test
        @DisplayName("skips when event.email is blank")
        void blankEmailSkips() throws Exception {
            UserActivatedEvent event = UserActivatedEvent.builder()
                    .userId(UUID.randomUUID()).email("").name("X").password("Tmp@123").locale("vi_VN").build();
            when(objectMapper.readValue("v", UserActivatedEvent.class)).thenReturn(event);

            listener.handleOnUserActivated("v");

            verifyNoInteractions(emailService);
        }

        @Test
        @DisplayName("rethrows RuntimeException for retry on downstream EmailService failure")
        void downstreamFailureRetries() throws Exception {
            UserActivatedEvent event = eventBuilder("vi_VN", "https://pay.example/1");
            when(objectMapper.readValue("v", UserActivatedEvent.class)).thenReturn(event);
            doThrow(new RuntimeException("smtp down"))
                    .when(emailService).sendEmail(any(), any(), any(), any());

            assertThatThrownBy(() -> listener.handleOnUserActivated("v"))
                    .isInstanceOf(RuntimeException.class);
        }
    }
}
