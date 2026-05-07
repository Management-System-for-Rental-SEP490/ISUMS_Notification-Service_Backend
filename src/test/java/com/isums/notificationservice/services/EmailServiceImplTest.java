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
        // Use real resilience4j instances (no-op behaviour) to avoid mocking complex static behaviours
        sesRateLimiter = RateLimiter.ofDefaults("test");
        sesRetry = Retry.ofDefaults("test");
        ReflectionTestUtils.setField(service, "sesRateLimiter", sesRateLimiter);
        ReflectionTestUtils.setField(service, "sesRetry", sesRetry);
        ReflectionTestUtils.setField(service, "from", "no-reply@isums.pro");
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
        @DisplayName("throws IllegalArgumentException when variable not allowed")
        void invalidVar() {
            when(templateService.getActive("welcome", LocaleType.vi_VN)).thenReturn(tpl());

            assertThatThrownBy(() -> service.sendEmail(
                    "a@b.com", "welcome", LocaleType.vi_VN,
                    Map.of("notAllowed", "x")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not allowed");
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
    }
}
