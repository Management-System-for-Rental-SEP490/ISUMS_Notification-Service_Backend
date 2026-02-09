package com.isums.notificationservice.configurations;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class SesResilienceConfig {

    @Bean
    public RateLimiter sesRateLimiter(@Value("${app.mail.maxPerSecond:14}") int maxPerSecond) {
        RateLimiterConfig cfg = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(maxPerSecond)
                .timeoutDuration(Duration.ofSeconds(3))
                .build();
        return RateLimiter.of("ses", cfg);
    }

    @Bean
    public Retry sesRetry() {
        RetryConfig cfg = RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(500))
                .retryOnException(ex -> {
                    return true;
                })
                .build();
        return Retry.of("ses", cfg);
    }
}
