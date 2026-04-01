package com.isums.notificationservice.domains.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfirmAndSendToTenantEvent {
    private String messageId;
    private UUID recipientUserId;
    private UUID contractId;
    private String contractName;
    private String url;
    private String confirmUrl;
    private Instant startDate;
    private Instant endDate;
}