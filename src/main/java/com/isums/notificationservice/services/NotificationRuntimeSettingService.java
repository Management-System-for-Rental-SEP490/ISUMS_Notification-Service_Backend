package com.isums.notificationservice.services;

import com.isums.notificationservice.domains.entities.NotificationRuntimeSetting;
import com.isums.notificationservice.infrastructures.repositories.NotificationRuntimeSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationRuntimeSettingService {

    public static final String KEY_VOICE_PROVIDER = "voice.provider";

    private static final String CACHE_PREFIX = "notif:setting:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final NotificationRuntimeSettingRepository repo;
    private final StringRedisTemplate redis;

    public Optional<String> get(String key) {
        String cacheKey = CACHE_PREFIX + key;
        String cached = redis.opsForValue().get(cacheKey);
        if (cached != null) {
            return Optional.of(cached);
        }
        return repo.findById(key).map(s -> {
            redis.opsForValue().set(cacheKey, s.getSettingValue(), CACHE_TTL);
            return s.getSettingValue();
        });
    }

    public String getOrDefault(String key, String defaultValue) {
        return get(key).orElse(defaultValue);
    }

    @Transactional
    public NotificationRuntimeSetting set(String key, String value, UUID updatedBy) {
        NotificationRuntimeSetting entity = repo.findById(key)
                .orElseGet(() -> NotificationRuntimeSetting.builder().settingKey(key).build());
        entity.setSettingValue(value);
        entity.setUpdatedBy(updatedBy);
        NotificationRuntimeSetting saved = repo.save(entity);
        redis.delete(CACHE_PREFIX + key);
        log.info("[RuntimeSetting] {} = {} by={}", key, value, updatedBy);
        return saved;
    }

    public void invalidate(String key) {
        redis.delete(CACHE_PREFIX + key);
    }
}
