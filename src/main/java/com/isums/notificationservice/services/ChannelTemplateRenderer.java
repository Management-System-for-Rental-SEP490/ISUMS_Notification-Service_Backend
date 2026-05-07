package com.isums.notificationservice.services;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.MustacheFactory;
import com.isums.notificationservice.domains.entities.ChannelTemplateVersion;
import com.isums.notificationservice.domains.enums.LocaleType;
import com.isums.notificationservice.domains.enums.NotificationChannel;
import com.isums.notificationservice.domains.enums.TemplateStatus;
import com.isums.notificationservice.exceptions.NotFoundException;
import com.isums.notificationservice.infrastructures.repositories.ChannelTemplateVersionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.StringReader;
import java.util.Map;

/**
 * Loads an ACTIVE channel template version and Mustache-renders body +
 * title (+ SSML when set). Missing template on the requested locale
 * falls back to {@link LocaleType#vi_VN} — matches the email template
 * service behaviour.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChannelTemplateRenderer {

    private final ChannelTemplateVersionRepository versionRepo;
    private final MustacheFactory mf = new DefaultMustacheFactory();

    public record RenderedTemplate(
            String body,
            String title,
            String ssml,
            ChannelTemplateVersion version
    ) {}

    public RenderedTemplate render(String templateKey, NotificationChannel channel,
                                     LocaleType locale, Map<String, Object> vars) {
        ChannelTemplateVersion version = versionRepo
                .findFirstByTemplate_TemplateKeyAndTemplate_ChannelAndLocaleAndStatusOrderByVersionDesc(
                        templateKey, channel, locale, TemplateStatus.ACTIVE)
                .or(() -> {
                    if (locale != LocaleType.vi_VN) {
                        log.warn("Template {} for {} locale={} missing — falling back vi_VN",
                                templateKey, channel, locale);
                        return versionRepo
                                .findFirstByTemplate_TemplateKeyAndTemplate_ChannelAndLocaleAndStatusOrderByVersionDesc(
                                        templateKey, channel, LocaleType.vi_VN, TemplateStatus.ACTIVE);
                    }
                    return java.util.Optional.empty();
                })
                .orElseThrow(() -> new NotFoundException(
                        "No ACTIVE template for key=" + templateKey
                                + " channel=" + channel + " locale=" + locale));

        String body  = renderOne(version.getBody(),  vars, "body");
        String title = version.getTitle() == null ? null
                : renderOne(version.getTitle(), vars, "title");
        String ssml  = version.getSsml() == null  ? null
                : renderOne(version.getSsml(),  vars, "ssml");

        return new RenderedTemplate(body, title, ssml, version);
    }

    private String renderOne(String tpl, Map<String, Object> vars, String label) {
        try (var sw = new java.io.StringWriter()) {
            var mustache = mf.compile(new StringReader(tpl), label);
            mustache.execute(sw, vars).flush();
            return sw.toString();
        } catch (Exception e) {
            throw new RuntimeException("render " + label + " failed: " + e.getMessage(), e);
        }
    }
}
