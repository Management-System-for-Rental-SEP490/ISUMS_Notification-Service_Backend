package com.isums.notificationservice.domains.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Local copy of the payload emitted by asset-service on topic
 * {@code utility.consumption.alert}. Keeping this class mirrored in
 * each service (rather than importing a shared jar) follows the
 * project convention already used for DepositPaidEvent and
 * PowerCutConfirmedEvent — lets each service deserialise with Jackson
 * without pulling asset-service's classpath.
 *
 * <p>Schema evolution: add fields at the end (nullable) and enable
 * {@code FAIL_ON_UNKNOWN_PROPERTIES=false} on the ObjectMapper. If a
 * field must be removed, bump the topic name to *.v2 on both ends.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UtilityThresholdExceededEvent {
    private String eventId;
    private String houseId;
    private String houseName;
    private String landlordUserId; // legacy field; older publishers stored the renter here
    private String tenantUserId;
    private String metric;          // ELECTRICITY | WATER
    private String previousStatus;  // GOOD | WARNING | CRITICAL | NO_DATA
    private String currentStatus;   // WARNING | CRITICAL
    private Double currentUsage;
    private Double monthlyLimit;
    private Double usagePercent;
    private String unit;
    private String month;           // yyyy-MM
    private Long occurredAt;        // epoch millis
}
