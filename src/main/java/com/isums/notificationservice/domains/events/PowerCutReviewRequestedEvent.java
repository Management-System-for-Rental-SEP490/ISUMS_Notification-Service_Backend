package com.isums.notificationservice.domains.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PowerCutReviewRequestedEvent {
    private UUID contractId;
    private UUID houseId;
    private UUID managerId;
    private String tenantName;
    private int daysLate;
    private Long totalAmount;
    private String messageId;
}
