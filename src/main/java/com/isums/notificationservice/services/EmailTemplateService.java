package com.isums.notificationservice.services;

import com.isums.notificationservice.domains.entities.EmailTemplate;
import com.isums.notificationservice.domains.enums.LocaleType;
import com.isums.notificationservice.domains.enums.TemplateStatus;
import com.isums.notificationservice.domains.dtos.EmailTemplateCached;
import com.isums.notificationservice.domains.entities.EmailTemplateVersion;
import com.isums.notificationservice.infrastructures.repositories.EmailTemplateRepository;
import com.isums.notificationservice.infrastructures.repositories.EmailTemplateVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmailTemplateService {
    public static final String CACHE = "emailTemplates";

    private final EmailTemplateRepository templateRepo;
    private final EmailTemplateVersionRepository versionRepo;

    @Cacheable(cacheNames = CACHE, key = "#templateKey + ':' + #locale")
    @Transactional(readOnly = true)
        public EmailTemplateCached getActive(String templateKey, LocaleType locale) {
        var v = versionRepo
                .findFirstByTemplate_TemplateKeyAndLocaleAndStatusOrderByVersionDesc(templateKey, locale, TemplateStatus.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("No ACTIVE template: " + templateKey + " / " + locale));

        return new EmailTemplateCached(
                v.getVersion(),
                v.getSubjectTpl(),
                v.getHtmlTpl(),
                v.getTextTpl(),
                v.getAllowedVars()
        );
    }

    @CacheEvict(cacheNames = CACHE, key = "#templateKey + ':' + #locale")
    @Transactional
    public EmailTemplateVersion publishNewVersion(
            String templateKey,
            LocaleType locale,
            String subjectTpl,
            String htmlTpl,
            String textTpl,
            java.util.List<String> allowedVars,
            String actor
    ) {
        EmailTemplate template = templateRepo.findByTemplateKey(templateKey)
                .orElseThrow(() -> new IllegalStateException("Template not found: " + templateKey));

        int nextVersion = versionRepo.findLatestForUpdate(template.getId(), locale)
                .map(v -> v.getVersion() + 1)
                .orElse(1);

        versionRepo.findActiveForUpdate(template.getId(), locale).ifPresent(active -> {
            active.setStatus(TemplateStatus.DEPRECATED);
            active.setUpdatedBy(actor);
            versionRepo.save(active);
        });

        EmailTemplateVersion v = EmailTemplateVersion.builder()
                .template(template)
                .locale(locale)
                .version(nextVersion)
                .status(TemplateStatus.ACTIVE)
                .subjectTpl(subjectTpl)
                .htmlTpl(htmlTpl)
                .textTpl(textTpl)
                .allowedVars(allowedVars)
                .createdBy(actor)
                .updatedBy(actor)
                .build();

        return versionRepo.save(v);
    }

    @CacheEvict(cacheNames = CACHE, key = "#templateKey + ':' + #locale")
    @Transactional
    public EmailTemplateVersion updateActiveContent(
            String templateKey,
            LocaleType locale,
            String subjectTpl,
            String htmlTpl,
            String textTpl,
            java.util.List<String> allowedVars,
            String actor
    ) {
        EmailTemplate template = templateRepo.findByTemplateKey(templateKey)
                .orElseThrow(() -> new IllegalStateException("Template not found: " + templateKey));

        EmailTemplateVersion active = versionRepo.findActiveForUpdate(template.getId(), locale)
                .orElseThrow(() -> new IllegalStateException("No ACTIVE template: " + templateKey + " / " + locale));

        active.setSubjectTpl(subjectTpl);
        active.setHtmlTpl(htmlTpl);
        active.setTextTpl(textTpl);
        active.setAllowedVars(allowedVars);
        active.setUpdatedBy(actor);
        return versionRepo.save(active);
    }
}
