package com.isums.notificationservice.domains.enums;

/**
 * Three-level severity classification used by the routing matrix.
 *
 * <ul>
 *   <li>{@code CRITICAL} — immediate safety risk (gas leak, fire, power
 *       lost, water leak). Voice + SMS to tenant AND landlord; manager
 *       gets SMS (voice as fallback if landlord doesn't ack).</li>
 *   <li>{@code WARNING} — attention needed but not immediate (high temp,
 *       gas warning, EIF anomaly). Voice tenant, SMS landlord, email
 *       digest manager.</li>
 *   <li>{@code INFO} — good news / status (power restored). Push +
 *       email tenant only; landlord/manager skip.</li>
 * </ul>
 */
public enum AlertSeverity {
    CRITICAL,
    WARNING,
    INFO
}
