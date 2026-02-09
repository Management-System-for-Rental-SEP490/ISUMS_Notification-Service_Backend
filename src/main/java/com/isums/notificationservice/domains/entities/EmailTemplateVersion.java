package com.isums.notificationservice.domains.entities;

import com.isums.notificationservice.domains.enums.LocaleType;
import com.isums.notificationservice.domains.enums.TemplateStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "EmailTemplateVersions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_tpl_locale_version",
                columnNames = {"template_id", "locale", "version"}
        ),
        indexes = @Index(name = "ix_tplver_template_locale_status", columnList = "template_id, locale, status")
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailTemplateVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private EmailTemplate template;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private LocaleType locale;

    @Column(nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TemplateStatus status;

    @Column(nullable = false, columnDefinition = "text")
    private String subjectTpl;

    @Column(nullable = false, columnDefinition = "text")
    private String htmlTpl;

    @Column(columnDefinition = "text")
    private String textTpl;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> allowedVars;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    private String createdBy;

    private String updatedBy;
}
