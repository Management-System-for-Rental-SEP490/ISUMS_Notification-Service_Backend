package com.isums.notificationservice.domains.events;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/**
 * Tenant account activated. Carries the plaintext temp password so the
 * welcome email can show it — tenant logs in with it, Keycloak forces
 * password change on first login.
 */
@Builder
public record UserActivatedEvent(
        UUID userId,
        String email,
        String name,
        String password,
        String locale,
        String firstRentPaymentUrl,
        Long firstRentAmount,
        Instant firstRentDueDate
) {
}
