package com.isums.notificationservice.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MonthlyQuotaResetScheduler {

    private final NotificationQuotaService quotaService;

    // 00:05 on the 1st of every month (VN time). The 5-minute delay is
    // insurance against clock skew between the scheduler host and Redis's
    // TTL expiry, which runs on the Redis-cloud server in a different zone.
    @Scheduled(cron = "0 5 0 1 * *", zone = "Asia/Ho_Chi_Minh")
    public void resetMonthly() {
        log.info("[QuotaReset] triggered");
        try {
            quotaService.resetAllUsageCounters();
        } catch (Exception e) {
            log.error("[QuotaReset] failed: {}", e.getMessage(), e);
        }
    }
}
