package com.isums.notificationservice.domains.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IssueWorkSlotAssignedEvent {
    private UUID referenceId;
    private UUID tenantId;
    private UUID houseId;
    private UUID slotId;
    private UUID staffId;
    private String referenceType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String action;
}
