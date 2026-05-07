package com.isums.notificationservice.domains.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PowerCutConfirmedEvent {
    private UUID contractId;
    private UUID houseId;
    private UUID tenantId;
    private UUID confirmedBy;
    private Instant executeAt;
    private String messageId;
}
