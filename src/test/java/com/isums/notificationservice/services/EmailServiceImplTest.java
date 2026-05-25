package com.isums.notificationservice.services;

import com.isums.notificationservice.domains.dtos.EmailTemplateCached;
import com.isums.notificationservice.domains.enums.LocaleType;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;
import jakarta.mail.Address;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailServiceImpl")
class EmailServiceImplTest {

    @Mock private EmailTemplateService templateService;
    @Mock private JavaMailSender mailSender;

    @InjectMocks private EmailServiceImpl service;

    private RateLimiter sesRateLimiter;
    private Retry sesRetry;

    @BeforeEach
    void setUp() {
        sesRateLimiter = RateLimiter.ofDefaults("test");
        sesRetry = Retry.ofDefaults("test");
        ReflectionTestUtils.setField(service, "sesRateLimiter", sesRateLimiter);
        ReflectionTestUtils.setField(service, "sesRetry", sesRetry);
        ReflectionTestUtils.setField(service, "from", "no-reply@isums.pro");
        ReflectionTestUtils.setField(service, "fromNameVi", "Hệ Thống ISUMS");
        ReflectionTestUtils.setField(service, "fromNameEn", "ISUMS System");
        ReflectionTestUtils.setField(service, "fromNameJa", "ISUMSシステム");
    }

    private EmailTemplateCached tpl() {
        return new EmailTemplateCached(
                1,
                "Xin chào {{name}}",
                "<h1>Xin chào {{name}}</h1>",
                "Xin chào {{name}}",
                List.of("name"));
    }

    @Nested
    @DisplayName("sendEmail")
    class Send {

        @Test
        @DisplayName("renders subject/html/text and sends MimeMessage on happy path")
        void happy() {
            when(templateService.getActive("welcome", LocaleType.vi_VN)).thenReturn(tpl());
            MimeMessage mime = new MimeMessage((jakarta.mail.Session) null);
            when(mailSender.createMimeMessage()).thenReturn(mime);

            service.sendEmail("alice@example.com", "welcome", LocaleType.vi_VN,
                    Map.of("name", "Alice"));

            verify(mailSender).send(mime);
        }

        @Test
        @DisplayName("logs warning but does not throw when variable not in allowedVars (Mustache ignores extras)")
        void invalidVar() {
            EmailTemplateCached restricted = new EmailTemplateCached(
                    1, "Subj {{name}}", "<h1>{{name}}</h1>", null, List.of("name"));
            when(templateService.getActive("welcome", LocaleType.vi_VN)).thenReturn(restricted);
            when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((jakarta.mail.Session) null));

            service.sendEmail("a@b.com", "welcome", LocaleType.vi_VN,
                    Map.of("name", "X", "notAllowed", "y"));

            verify(mailSender).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("propagates MailException from mailSender")
        void mailException() {
            when(templateService.getActive("welcome", LocaleType.vi_VN)).thenReturn(tpl());
            when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((jakarta.mail.Session) null));
            doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

            assertThatThrownBy(() -> service.sendEmail(
                    "a@b.com", "welcome", LocaleType.vi_VN, Map.of("name", "A")))
                    .isInstanceOf(MailSendException.class);
        }

        @Test
        @DisplayName("allows any vars when template has no allowedVars restriction")
        void noRestriction() {
            EmailTemplateCached unrestricted = new EmailTemplateCached(
                    1, "Subj", "<h1>H</h1>", null, List.of());
            when(templateService.getActive("open", LocaleType.vi_VN)).thenReturn(unrestricted);
            when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((jakarta.mail.Session) null));

            service.sendEmail("a@b.com", "open", LocaleType.vi_VN, Map.of("anything", "ok"));

            verify(mailSender).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("falls back to vi_VN when en_US template missing (foreign tenant before en seed)")
        void enUsMissingFallsBackToViVn() {
            when(templateService.getActive("user_activated", LocaleType.en_US))
                    .thenThrow(new IllegalStateException("No ACTIVE template: user_activated / en_US"));
            when(templateService.getActive("user_activated", LocaleType.vi_VN)).thenReturn(tpl());
            when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((jakarta.mail.Session) null));

            service.sendEmail("john@example.com", "user_activated", LocaleType.en_US,
                    Map.of("name", "John"));

            verify(mailSender).send(any(MimeMessage.class));
            verify(templateService).getActive("user_activated", LocaleType.en_US);
            verify(templateService).getActive("user_activated", LocaleType.vi_VN);
        }

        @Test
        @DisplayName("falls back to vi_VN when ja_JP template missing")
        void jaJpMissingFallsBackToViVn() {
            when(templateService.getActive("user_activated", LocaleType.ja_JP))
                    .thenThrow(new IllegalStateException("No ACTIVE template: user_activated / ja_JP"));
            when(templateService.getActive("user_activated", LocaleType.vi_VN)).thenReturn(tpl());
            when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((jakarta.mail.Session) null));

            service.sendEmail("yamada@example.jp", "user_activated", LocaleType.ja_JP,
                    Map.of("name", "Yamada"));

            verify(mailSender).send(any(MimeMessage.class));
        }

        @Test
        @DisplayName("does NOT silently swallow when vi_VN itself is missing — surfaces the error")
        void viVnMissingPropagates() {
            when(templateService.getActive("ghost", LocaleType.vi_VN))
                    .thenThrow(new IllegalStateException("No ACTIVE template: ghost / vi_VN"));

            assertThatThrownBy(() -> service.sendEmail(
                    "a@b.com", "ghost", LocaleType.vi_VN, Map.of("name", "A")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No ACTIVE template");
        }

        @Test
        @DisplayName("uses en_US directly when template exists (foreign tenant English seeded)")
        void enUsDirectWhenAvailable() {
            EmailTemplateCached enTpl = new EmailTemplateCached(
                    1, "Welcome {{name}}", "<h1>Welcome {{name}}</h1>", null, List.of("name"));
            when(templateService.getActive("user_activated", LocaleType.en_US)).thenReturn(enTpl);
            when(mailSender.createMimeMessage()).thenReturn(new MimeMessage((jakarta.mail.Session) null));

            service.sendEmail("john@example.com", "user_activated", LocaleType.en_US,
                    Map.of("name", "John"));

            verify(mailSender).send(any(MimeMessage.class));
        }
    }

    @Nested
    @DisplayName("From display name per locale")
    class FromName {

        private InternetAddress captureFromAfterSend(LocaleType locale) throws Exception {
            EmailTemplateCached anyTpl = new EmailTemplateCached(
                    1, "Subj {{name}}", "<h1>{{name}}</h1>", null, List.of("name"));
            when(templateService.getActive(any(String.class), any(LocaleType.class)))
                    .thenReturn(anyTpl);

            Session realSession = Session.getInstance(new Properties());
            MimeMessage mime = new MimeMessage(realSession);
            when(mailSender.createMimeMessage()).thenReturn(mime);

            service.sendEmail("recipient@example.com", "welcome", locale, Map.of("name", "X"));

            ArgumentCaptor<MimeMessage> cap = ArgumentCaptor.forClass(MimeMessage.class);
            verify(mailSender).send(cap.capture());
            Address[] fromArr = cap.getValue().getFrom();
            assertThat(fromArr).hasSize(1);
            return (InternetAddress) fromArr[0];
        }

        @Test
        @DisplayName("vi_VN renders From display name 'Hệ Thống ISUMS'")
        void viVn() throws Exception {
            InternetAddress addr = captureFromAfterSend(LocaleType.vi_VN);
            assertThat(addr.getPersonal()).isEqualTo("Hệ Thống ISUMS");
            assertThat(addr.getAddress()).isEqualTo("no-reply@isums.pro");
        }

        @Test
        @DisplayName("en_US renders From display name 'ISUMS System'")
        void enUs() throws Exception {
            InternetAddress addr = captureFromAfterSend(LocaleType.en_US);
            assertThat(addr.getPersonal()).isEqualTo("ISUMS System");
            assertThat(addr.getAddress()).isEqualTo("no-reply@isums.pro");
        }

        @Test
        @DisplayName("ja_JP renders From display name 'ISUMSシステム' (Japanese)")
        void jaJp() throws Exception {
            InternetAddress addr = captureFromAfterSend(LocaleType.ja_JP);
            assertThat(addr.getPersonal()).isEqualTo("ISUMSシステム");
            assertThat(addr.getAddress()).isEqualTo("no-reply@isums.pro");
        }

        @Test
        @DisplayName("null locale falls back to vi_VN From name")
        void nullLocaleFallsBackToViVn() throws Exception {
            InternetAddress addr = captureFromAfterSend(null);
            assertThat(addr.getPersonal()).isEqualTo("Hệ Thống ISUMS");
        }

        @Test
        @DisplayName("when configured fromName is blank, From has no personal (address only)")
        void blankFromNameSkipsPersonal() throws Exception {
            ReflectionTestUtils.setField(service, "fromNameVi", "");
            InternetAddress addr = captureFromAfterSend(LocaleType.vi_VN);
            assertThat(addr.getPersonal()).isNull();
            assertThat(addr.getAddress()).isEqualTo("no-reply@isums.pro");
        }

        @Test
        @DisplayName("From header encoded as RFC 2047 UTF-8 for non-ASCII (Vietnamese)")
        void rfc2047Encoded() throws Exception {
            EmailTemplateCached anyTpl = new EmailTemplateCached(
                    1, "S", "<p>B</p>", null, List.of());
            when(templateService.getActive(any(String.class), any(LocaleType.class)))
                    .thenReturn(anyTpl);
            Session realSession = Session.getInstance(new Properties());
            MimeMessage mime = new MimeMessage(realSession);
            when(mailSender.createMimeMessage()).thenReturn(mime);

            service.sendEmail("r@example.com", "welcome", LocaleType.vi_VN, Map.of());

            ArgumentCaptor<MimeMessage> cap = ArgumentCaptor.forClass(MimeMessage.class);
            verify(mailSender).send(cap.capture());
            String fromHeader = cap.getValue().getHeader("From", null);
            assertThat(fromHeader)
                    .as("Should be RFC 2047 base64-or-q encoded with UTF-8 charset")
                    .matches("=\\?(?i)UTF-8\\?[BQ]\\?[^?]+\\?=.*<no-reply@isums\\.pro>");
        }
    }
}
