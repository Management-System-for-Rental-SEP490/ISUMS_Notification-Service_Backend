package com.isums.notificationservice.domains.enums;

/**
 * Event types dispatched through the multi-channel notification pipeline.
 * Must match the {@code event_type} string used in template seeding and
 * the payload from the esp32 Lambda tier.
 *
 * <p>Severity hint: *_CRITICAL events bypass quiet hours when the user's
 * {@code quiet_hours_override_critical} is TRUE (default).
 */
public enum AlertEventType {
    // Critical — safety / immediate action
    GAS_CRITICAL,           // MQ2 over 300 ppm
    FIRE_CRITICAL,          // temperature > 55°C
    POWER_LOST,             // controller reports PZEM outage
    WATER_LEAK_SUSPECTED,   // > 10 min continuous flow

    // Warning — attention needed but not immediate
    GAS_WARNING,
    TEMPERATURE_HIGH,
    HUMIDITY_HIGH,
    HUMIDITY_LOW,
    VOLTAGE_ABNORMAL,
    CURRENT_HIGH,
    POWER_HIGH,
    FREQUENCY_ABNORMAL,
    WATER_FLOW_HIGH,

    // Utility monthly threshold alerts from Asset-Service
    UTILITY_ELECTRICITY_WARNING,
    UTILITY_WATER_WARNING,
    UTILITY_ELECTRICITY_CRITICAL,
    UTILITY_WATER_CRITICAL,

    // Info — good news
    POWER_RESTORED,

    // AI-derived anomaly
    EIF_ANOMALY_POWER,
    EIF_ANOMALY_WATER;

    public boolean isCritical() {
        return severity() == AlertSeverity.CRITICAL;
    }

    /**
     * Maps the event type to its three-level severity used by the
     * routing matrix in {@code NotificationDispatchService}.
     */
    public AlertSeverity severity() {
        return switch (this) {
            case GAS_CRITICAL, FIRE_CRITICAL, POWER_LOST, WATER_LEAK_SUSPECTED,
                 UTILITY_ELECTRICITY_CRITICAL, UTILITY_WATER_CRITICAL -> AlertSeverity.CRITICAL;
            case POWER_RESTORED -> AlertSeverity.INFO;
            default -> AlertSeverity.WARNING;
        };
    }
}
