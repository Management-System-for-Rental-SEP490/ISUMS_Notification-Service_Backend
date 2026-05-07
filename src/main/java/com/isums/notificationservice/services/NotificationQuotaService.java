package com.isums.notificationservice.services;

import com.isums.notificationservice.domains.entities.NotificationSubscription;
import com.isums.notificationservice.infrastructures.repositories.NotificationSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Redis-backed rate limit + monthly quota. Hot-path reads never touch
 * Postgres — the {@code notification_subscriptions} counter is updated
 * periodically from Redis (eventual consistency is fine for billing
 * reports; enforcement happens in Redis).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationQuotaService {

    private static final DateTimeFormatter MONTH_KEY =
            DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final StringRedisTemplate redis;
    private final NotificationSubscriptionRepository subsRepo;

    /** Returns true if we claimed a slot; false if the user is still in cool-down. */
    public boolean tryAcquireVoiceRateLimit(UUID userId, int cooldownSec) {
        String key = "notif:voice:ratelimit:" + userId;
        Boolean ok = redis.opsForValue().setIfAbsent(key, "1", Duration.ofSeconds(cooldownSec));
        return Boolean.TRUE.equals(ok);
    }

    public long remainingRateLimitSec(UUID userId) {
        Long ttl = redis.getExpire("notif:voice:ratelimit:" + userId);
        return ttl == null || ttl < 0 ? 0 : ttl;
    }

    /** Returns true + increments; false if the monthly quota is already used up. */
    @Transactional
    public boolean tryConsumeVoiceQuota(UUID userId) {
        NotificationSubscription sub = subsRepo.findById(userId).orElse(null);
        if (sub == null) return false;

        int quota = sub.getVoiceQuotaMonthly();
        if (quota <= 0) return false;

        String month = MONTH_KEY.format(Instant.now());
        String key = "notif:voice:quota:" + userId + ":" + month;

        Long used = redis.opsForValue().increment(key);
        if (used == null) return false;

        // First time this month — set a 40-day expiry so it auto-clears.
        if (used == 1L) {
            redis.expire(key, Duration.ofDays(40));
        }

        if (used > quota) {
            // Over cap — decrement to avoid drift then reject.
            redis.opsForValue().decrement(key);
            log.info("[Quota] voice quota exceeded userId={} used={}/{}", userId, used - 1, quota);
            return false;
        }

        // Mirror into Postgres best-effort. A crash before commit means the
        // DB counter lags Redis by one — acceptable; nightly reconciler
        // can recover if needed.
        sub.setVoiceUsedThisMonth(used.intValue());
        subsRepo.save(sub);
        return true;
    }

    @Transactional
    public void refundVoiceQuota(UUID userId) {
        String month = MONTH_KEY.format(Instant.now());
        redis.opsForValue().decrement("notif:voice:quota:" + userId + ":" + month);
        subsRepo.findById(userId).ifPresent(sub -> {
            if (sub.getVoiceUsedThisMonth() > 0) {
                sub.setVoiceUsedThisMonth(sub.getVoiceUsedThisMonth() - 1);
                subsRepo.save(sub);
            }
        });
    }

    /** Called by monthly scheduler on the 1st of every month (VN time). */
    @Transactional
    public void resetAllUsageCounters() {
        int updated = 0;
        for (NotificationSubscription sub : subsRepo.findAll()) {
            sub.setVoiceUsedThisMonth(0);
            sub.setSmsUsedThisMonth(0);
            sub.setQuotaResetAt(Instant.now());
            subsRepo.save(sub);
            updated++;
        }
        log.info("[Quota] monthly reset complete, rows={}", updated);
    }

    public int readVoiceUsedThisMonth(UUID userId) {
        String month = MONTH_KEY.format(Instant.now());
        String v = redis.opsForValue().get("notif:voice:quota:" + userId + ":" + month);
        try {
            return v == null ? 0 : Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static String currentMonthKey() {
        return MONTH_KEY.format(Instant.now());
    }

    public static LocalDate currentMonthDate() {
        return LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).withDayOfMonth(1);
    }

    // ─── Test-call daily quota ─────────────────────────────────────────
    // PDPL + UX guard: tenant clicking "Gọi thử" repeatedly would burn
    // their monthly voice quota and could be construed as harassment by
    // an angry roommate. One test call per calendar day per user is more
    // than enough to verify the phone is correct.

    private static final DateTimeFormatter DAY_KEY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final int TEST_VOICE_DAILY_LIMIT = 1;

    /**
     * Returns true if we claimed today's test-voice slot. Once consumed,
     * blocks further attempts until the next VN midnight. Counts against
     * the monthly voice quota too — call {@link #tryConsumeVoiceQuota}
     * separately after this check passes.
     */
    public boolean tryAcquireTestVoiceDaily(UUID userId) {
        String key = "notif:voice:testday:" + userId + ":" + DAY_KEY.format(Instant.now());
        Long count = redis.opsForValue().increment(key);
        if (count == null) return false;
        if (count == 1L) {
            // ~25h TTL absorbs DST-style timezone edge cases.
            redis.expire(key, Duration.ofHours(25));
        }
        if (count > TEST_VOICE_DAILY_LIMIT) {
            redis.opsForValue().decrement(key);
            return false;
        }
        return true;
    }
}
