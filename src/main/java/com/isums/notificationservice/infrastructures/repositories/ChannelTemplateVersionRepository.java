package com.isums.notificationservice.infrastructures.repositories;

import com.isums.notificationservice.domains.entities.ChannelTemplateVersion;
import com.isums.notificationservice.domains.enums.LocaleType;
import com.isums.notificationservice.domains.enums.NotificationChannel;
import com.isums.notificationservice.domains.enums.TemplateStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChannelTemplateVersionRepository extends JpaRepository<ChannelTemplateVersion, UUID> {

    Optional<ChannelTemplateVersion>
    findFirstByTemplate_TemplateKeyAndTemplate_ChannelAndLocaleAndStatusOrderByVersionDesc(
            String templateKey, NotificationChannel channel, LocaleType locale, TemplateStatus status);
}
