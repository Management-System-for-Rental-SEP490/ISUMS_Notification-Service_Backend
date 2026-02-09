package com.isums.notificationservice.infrastructures.repositories;

import com.isums.notificationservice.domains.entities.EmailTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmailTemplateRepository extends JpaRepository<EmailTemplate, UUID> {
    Optional<EmailTemplate> findByTemplateKey(String templateKey);
}
