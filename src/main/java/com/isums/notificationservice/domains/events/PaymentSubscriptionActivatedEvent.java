package com.isums.notificationservice.domains.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted by Payment-Service after a successful VNPay IPN for a
 * {@code SUBSCRIPTION} payment. Field names mirror the Map<String,Object>
 * the producer puts on {@code payment.subscription-activated} — change
 * either side and you must update the other in lockstep.
 *
 * <p>{@code months} is intentionally absent — Payment-Service moved to
 * {@code durationDays} so a 7-day trial doesn't have to round up to a
 * full month. Boxed wrappers everywhere so a missing field deserialises
 * to null instead of crashing the consumer (we saw this in prod when the
 * old primitive {@code int months} blew up on a {@code null} field).
 */
public record PaymentSubscriptionActivatedEvent(
        String  intentId,
        UUID    userId,
        UUID    houseId,
        String  purpose,
        Integer durationDays,
        String  planCode,
        String  planId,
        Long    amountVnd,
        String  provider,
        String  txnRef,
        String  txnNo,
        Instant paidAt
) {}
