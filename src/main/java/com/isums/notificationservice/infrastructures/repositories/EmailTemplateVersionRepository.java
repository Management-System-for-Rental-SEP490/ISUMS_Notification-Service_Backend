package com.isums.notificationservice.infrastructures.repositories;

import com.isums.notificationservice.domains.entities.EmailTemplateVersion;
import com.isums.notificationservice.domains.enums.LocaleType;
import com.isums.notificationservice.domains.enums.TemplateStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.Optional;
import java.util.UUID;

public interface EmailTemplateVersionRepository extends JpaRepository<EmailTemplateVersion, UUID> {

    Optional<EmailTemplateVersion>
    findFirstByTemplate_TemplateKeyAndLocaleAndStatusOrderByVersionDesc(String templateKey, LocaleType locale, TemplateStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    select v from EmailTemplateVersion v
    where v.template.id = :templateId and v.locale = :locale
    order by v.version desc
  """)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    Optional<EmailTemplateVersion> findLatestForUpdate(@Param("templateId") UUID templateId, @Param("locale") LocaleType locale);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    select v from EmailTemplateVersion v
    where v.template.id = :templateId and v.locale = :locale and v.status = 'ACTIVE'
    order by v.version desc
  """)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    Optional<EmailTemplateVersion> findActiveForUpdate(@Param("templateId") UUID templateId, @Param("locale") LocaleType locale);
}
