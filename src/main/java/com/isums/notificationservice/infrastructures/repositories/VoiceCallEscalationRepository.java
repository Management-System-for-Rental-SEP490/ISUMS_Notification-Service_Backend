package com.isums.notificationservice.infrastructures.repositories;

import com.isums.notificationservice.domains.entities.VoiceCallEscalation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface VoiceCallEscalationRepository extends JpaRepository<VoiceCallEscalation, UUID> {
}
