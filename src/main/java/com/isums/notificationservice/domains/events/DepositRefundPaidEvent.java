package com.isums.notificationservice.domains.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepositRefundPaidEvent {
    private UUID contractId;
    private UUID houseId;
    private UUID tenantId;
    private String tenantEmail;
    private Long refundAmount;
    private String paymentMethod;
    private String note;
    private Instant paidAt;
    private String messageId;
}
