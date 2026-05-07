package com.isums.notificationservice.domains.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContractCompletedEvent {
    private UUID contractId;
    private UUID tenantId;
    private String tenantEmail;
    private Boolean isNewAccount;
    private UUID houseId;
    private UUID landlordId;
    private Long depositAmount;
    private Long rentAmount;
    private Integer payDate;
    private Instant startAt;
    private Instant endAt;
    private Instant completedAt;
    private String signedPdfUrl;
}
