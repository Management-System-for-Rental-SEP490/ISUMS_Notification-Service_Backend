package com.isums.notificationservice.infrastructures.repositories;

import com.isums.notificationservice.domains.entities.VoiceAudioCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface VoiceAudioCacheRepository extends JpaRepository<VoiceAudioCache, UUID> {

    Optional<VoiceAudioCache> findByCacheKey(String cacheKey);
}
