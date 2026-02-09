package com.isums.notificationservice.services;

import com.isums.notificationservice.domains.dtos.EmailTemplateCached;
import com.isums.notificationservice.domains.enums.LocaleType;
import com.isums.notificationservice.infrastructures.abstracts.EmailService;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.retry.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.MustacheFactory;

import java.io.StringReader;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final EmailTemplateService templateService;
    private final JavaMailSender mailSender;
    private final RateLimiter sesRateLimiter;
    private final Retry sesRetry;
    private final MustacheFactory mf = new DefaultMustacheFactory();

    @Value("${app.mail.from}")
    private String from;

    public void sendEmail(String to, String templateKey, LocaleType locale, Map<String, Object> vars) {
        EmailTemplateCached tpl = templateService.getActive(templateKey, locale);
        validateVars(tpl, vars);

        String subject = render(tpl.subjectTpl(), vars);
        String html = render(tpl.htmlTpl(), vars);
        String text = (tpl.textTpl() == null) ? null : render(tpl.textTpl(), vars);

        try {
            var msg = mailSender.createMimeMessage();
            var h = new MimeMessageHelper(msg, true, UTF_8.name());
            h.setFrom(from);
            h.setTo(to);
            h.setSubject(subject);
            if (text != null) h.setText(text, html);
            else h.setText(html, true);

            Runnable send = () -> mailSender.send(msg);
            Runnable limited = RateLimiter.decorateRunnable(sesRateLimiter, send);
            Runnable retried = Retry.decorateRunnable(sesRetry, limited);

            retried.run();

            log.info("email_sent templateKey={} locale={} version={} to={}",
                    templateKey, locale, tpl.version(), to);
        } catch (MailException e) {
            log.error("email_send_failed templateKey={} locale={} to={} err={}",
                    templateKey, locale, to, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("email_build_or_send_failed templateKey={} locale={} to={} err={}",
                    templateKey, locale, to, e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private String render(String tpl, Map<String, Object> vars) {
        try (var sw = new java.io.StringWriter()) {
            var mustache = mf.compile(new StringReader(tpl), "tpl");
            mustache.execute(sw, vars).flush();
            return sw.toString();
        } catch (Exception e) {
            throw new RuntimeException("Render failed: " + e.getMessage(), e);
        }
    }

    private void validateVars(EmailTemplateCached tpl, Map<String, Object> vars) {
        if (tpl.allowedVars() == null || tpl.allowedVars().isEmpty()) return;
        for (String k : vars.keySet()) {
            if (!tpl.allowedVars().contains(k)) {
                throw new IllegalArgumentException("Variable not allowed: " + k);
            }
        }
    }
}
