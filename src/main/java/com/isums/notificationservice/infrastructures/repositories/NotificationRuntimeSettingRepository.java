package com.isums.notificationservice.infrastructures.repositories;

import com.isums.notificationservice.domains.entities.NotificationRuntimeSetting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRuntimeSettingRepository
        extends JpaRepository<NotificationRuntimeSetting, String> {
}
