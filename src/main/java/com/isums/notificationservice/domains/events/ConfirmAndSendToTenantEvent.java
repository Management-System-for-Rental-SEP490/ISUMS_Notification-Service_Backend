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
    private String recipientEmail;
    private String recipientName;
    private UUID contractId;
    private String contractName;
    private String url;
    private String confirmUrl;
    private Instant startDate;
    private Instant endDate;

    // Contract language code emitted by econtract-service so Notification
    // picks the right email template. Expected values: "VI", "VI_EN", "VI_JA".
    // Null = legacy event (pre BE-3) → fall back to VI.
    private String contractLanguage;
}
