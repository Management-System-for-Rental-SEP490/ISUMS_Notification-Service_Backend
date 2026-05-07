package com.isums.notificationservice.infrastructures.repositories;

import com.isums.notificationservice.domains.entities.ChannelTemplate;
import com.isums.notificationservice.domains.enums.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChannelTemplateRepository extends JpaRepository<ChannelTemplate, UUID> {

    Optional<ChannelTemplate> findByTemplateKeyAndChannel(String templateKey, NotificationChannel channel);
}
