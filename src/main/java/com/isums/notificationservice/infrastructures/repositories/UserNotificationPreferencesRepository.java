package com.isums.notificationservice.infrastructures.repositories;

import com.isums.notificationservice.domains.entities.UserNotificationPreferences;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UserNotificationPreferencesRepository
        extends JpaRepository<UserNotificationPreferences, UUID> {
}
