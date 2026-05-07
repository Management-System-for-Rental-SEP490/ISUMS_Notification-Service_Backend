package com.isums.notificationservice.infrastructures.repositories;

import com.isums.notificationservice.domains.entities.ManagerNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ManagerNotificationRepository extends JpaRepository<ManagerNotification, UUID> {

    Page<ManagerNotification> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId, Pageable pageable);

    long countByRecipientIdAndIsReadFalse(UUID recipientId);

    Optional<ManagerNotification> findByIdAndRecipientId(UUID id, UUID recipientId);

    @Modifying
    @Query("UPDATE ManagerNotification n SET n.isRead = true, n.readAt = :readAt WHERE n.recipientId = :recipientId AND n.isRead = false")
    void markAllReadByRecipientId(@Param("recipientId") UUID recipientId, @Param("readAt") Instant readAt);
}