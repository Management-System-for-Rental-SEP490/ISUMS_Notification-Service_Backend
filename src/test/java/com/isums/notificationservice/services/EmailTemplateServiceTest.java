package com.isums.notificationservice.services;

import com.isums.notificationservice.domains.dtos.EmailTemplateCached;
import com.isums.notificationservice.domains.entities.EmailTemplate;
import com.isums.notificationservice.domains.entities.EmailTemplateVersion;
import com.isums.notificationservice.domains.enums.LocaleType;
import com.isums.notificationservice.domains.enums.TemplateStatus;
import com.isums.notificationservice.infrastructures.repositories.EmailTemplateRepository;
import com.isums.notificationservice.infrastructures.repositories.EmailTemplateVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailTemplateService")
class EmailTemplateServiceTest {

    @Mock private EmailTemplateRepository templateRepo;
    @Mock private EmailTemplateVersionRepository versionRepo;

    @InjectMocks private EmailTemplateService service;

    private EmailTemplate template;
    private UUID templateId;

    @BeforeEach
    void setUp() {
        templateId = UUID.randomUUID();
        template = EmailTemplate.builder()
                .id(templateId).templateKey("welcome").build();
    }

    private EmailTemplateVersion versionV1() {
        return EmailTemplateVersion.builder()
                .template(template).locale(LocaleType.vi_VN).version(1)
                .status(TemplateStatus.ACTIVE)
                .subjectTpl("Hi {{name}}").htmlTpl("<h1>{{name}}</h1>").textTpl("Hi")
                .allowedVars(List.of("name")).build();
    }

    @Nested
    @DisplayName("getActive")
    class GetActive {

        @Test
        @DisplayName("returns cached projection from latest ACTIVE version")
        void returnsCached() {
            when(versionRepo.findFirstByTemplate_TemplateKeyAndLocaleAndStatusOrderByVersionDesc(
                    "welcome", LocaleType.vi_VN, TemplateStatus.ACTIVE))
                    .thenReturn(Optional.of(versionV1()));

            EmailTemplateCached cached = service.getActive("welcome", LocaleType.vi_VN);

            assertThat(cached.version()).isEqualTo(1);
            assertThat(cached.subjectTpl()).isEqualTo("Hi {{name}}");
            assertThat(cached.allowedVars()).containsExactly("name");
        }

        @Test
        @DisplayName("throws IllegalStateException when no ACTIVE version")
        void noActive() {
            when(versionRepo.findFirstByTemplate_TemplateKeyAndLocaleAndStatusOrderByVersionDesc(
                    "welcome", LocaleType.vi_VN, TemplateStatus.ACTIVE))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getActive("welcome", LocaleType.vi_VN))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("No ACTIVE template");
        }
    }

    @Nested
    @DisplayName("publishNewVersion")
    class PublishNew {

        @Test
        @DisplayName("creates v1 and saves ACTIVE when no prior version exists")
        void createsV1() {
            when(templateRepo.findByTemplateKey("welcome")).thenReturn(Optional.of(template));
            when(versionRepo.findLatestForUpdate(templateId, LocaleType.vi_VN))
                    .thenReturn(Optional.empty());
            when(versionRepo.findActiveForUpdate(templateId, LocaleType.vi_VN))
                    .thenReturn(Optional.empty());
            when(versionRepo.save(any(EmailTemplateVersion.class))).thenAnswer(a -> a.getArgument(0));

            EmailTemplateVersion result = service.publishNewVersion(
                    "welcome", LocaleType.vi_VN, "subj", "<h1/>", "text",
                    List.of("name"), "admin");

            assertThat(result.getVersion()).isEqualTo(1);
            assertThat(result.getStatus()).isEqualTo(TemplateStatus.ACTIVE);
            assertThat(result.getCreatedBy()).isEqualTo("admin");
        }

        @Test
        @DisplayName("bumps to v2 and deprecates previous ACTIVE when exists")
        void bumpsAndDeprecates() {
            EmailTemplateVersion prev = versionV1();
            when(templateRepo.findByTemplateKey("welcome")).thenReturn(Optional.of(template));
            when(versionRepo.findLatestForUpdate(templateId, LocaleType.vi_VN))
                    .thenReturn(Optional.of(prev));
            when(versionRepo.findActiveForUpdate(templateId, LocaleType.vi_VN))
                    .thenReturn(Optional.of(prev));
            when(versionRepo.save(any(EmailTemplateVersion.class))).thenAnswer(a -> a.getArgument(0));

            EmailTemplateVersion result = service.publishNewVersion(
                    "welcome", LocaleType.vi_VN, "s2", "<h1>v2</h1>", "t2",
                    List.of("name"), "admin");

            assertThat(result.getVersion()).isEqualTo(2);
            assertThat(prev.getStatus()).isEqualTo(TemplateStatus.DEPRECATED);
        }

        @Test
        @DisplayName("throws when template key missing")
        void templateMissing() {
            when(templateRepo.findByTemplateKey("missing")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.publishNewVersion(
                    "missing", LocaleType.vi_VN, "s", "h", "t", List.of(), "a"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Template not found");
        }
    }

    @Nested
    @DisplayName("updateActiveContent")
    class UpdateActive {

        @Test
        @DisplayName("patches subject/html/text/allowedVars on the ACTIVE row")
        void patches() {
            EmailTemplateVersion active = versionV1();
            when(templateRepo.findByTemplateKey("welcome")).thenReturn(Optional.of(template));
            when(versionRepo.findActiveForUpdate(templateId, LocaleType.vi_VN))
                    .thenReturn(Optional.of(active));
            when(versionRepo.save(active)).thenReturn(active);

            service.updateActiveContent("welcome", LocaleType.vi_VN,
                    "new-subject", "<p>new</p>", "new-text", List.of("x"), "admin");

            assertThat(active.getSubjectTpl()).isEqualTo("new-subject");
            assertThat(active.getHtmlTpl()).isEqualTo("<p>new</p>");
            assertThat(active.getAllowedVars()).containsExactly("x");
            assertThat(active.getUpdatedBy()).isEqualTo("admin");
        }

        @Test
        @DisplayName("throws when no ACTIVE version")
        void noActive() {
            when(templateRepo.findByTemplateKey("welcome")).thenReturn(Optional.of(template));
            when(versionRepo.findActiveForUpdate(templateId, LocaleType.vi_VN))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.updateActiveContent(
                    "welcome", LocaleType.vi_VN, "s", "h", "t", List.of(), "a"))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}
