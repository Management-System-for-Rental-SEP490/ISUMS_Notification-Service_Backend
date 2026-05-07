package com.isums.notificationservice.infrastructures.repositories;

import com.isums.notificationservice.domains.entities.VoiceCallJob;
import com.isums.notificationservice.domains.enums.VoiceCallStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface VoiceCallJobRepository extends JpaRepository<VoiceCallJob, UUID> {

    Optional<VoiceCallJob> findByProviderCallId(String providerCallId);

    List<VoiceCallJob> findAllByStatusInAndNextRetryAtBefore(
            List<VoiceCallStatus> statuses, Instant cutoff);

    Page<VoiceCallJob> findAllByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
