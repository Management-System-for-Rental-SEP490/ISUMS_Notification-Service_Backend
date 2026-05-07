package com.isums.notificationservice.domains.events;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContractReadyForLandlordSignatureEvent {
    private String messageId;
    private UUID contractId;
    private UUID recipientUserId;
    private UUID tenantId;
    private String tenantName;
    private String contractName;
    private String documentId;
}
