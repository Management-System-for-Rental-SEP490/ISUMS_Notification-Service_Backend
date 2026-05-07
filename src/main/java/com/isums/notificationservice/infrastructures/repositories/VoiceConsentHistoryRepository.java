package com.isums.notificationservice.infrastructures.repositories;

import com.isums.notificationservice.domains.entities.VoiceConsentHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface VoiceConsentHistoryRepository extends JpaRepository<VoiceConsentHistory, UUID> {
    /** Latest consent rows for a user — used by /preferences/me/consent-history. */
    Page<VoiceConsentHistory> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
