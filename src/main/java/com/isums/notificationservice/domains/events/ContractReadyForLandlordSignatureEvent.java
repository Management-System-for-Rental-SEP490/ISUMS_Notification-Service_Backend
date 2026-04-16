package com.isums.notificationservice.domains.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContractReadyForLandlordSignatureEvent {
    private UUID contractId;
    private UUID recipientId;
    private String tenantName;
    private String contractName;
    private String messageId;
}
