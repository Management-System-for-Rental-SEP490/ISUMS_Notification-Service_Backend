package com.isums.notificationservice.domains.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PowerCutRequestEvent {
    private UUID invoiceId;
    private UUID contractId;
    private UUID houseId;
    private UUID tenantId;
    private int daysLate;
    private Long totalAmount;
    private String messageId;
}
