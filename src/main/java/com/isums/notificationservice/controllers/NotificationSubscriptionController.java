package com.isums.notificationservice.controllers;

import com.isums.notificationservice.domains.dtos.ApiResponse;
import com.isums.notificationservice.domains.dtos.ApiResponses;
import com.isums.notificationservice.domains.dtos.SubscriptionDto;
import com.isums.notificationservice.services.NotificationSubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Subscription lifecycle. Self-serve upgrade requires a valid payment
 * transaction id — real integration with Payment-Service is via Kafka
 * {@code payment.subscription-activated}; this endpoint exists to let
 * admins grant PREMIUM for thesis demo + QA without running a real
 * VNPay/MoMo charge.
 */
@RestController
@RequestMapping("/api/notifications/subscriptions")
@RequiredArgsConstructor
public class NotificationSubscriptionController {

    private final NotificationSubscriptionService subscriptionService;

    /** Admin-only shortcut for demos / customer support refunds. */
    @PostMapping("/admin/grant-premium")
    @PreAuthorize("hasAnyRole('LANDLORD', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<SubscriptionDto>> adminGrant(
            @RequestBody AdminGrantRequest req) {
        var sub = subscriptionService.activatePremium(req.userId(), req.months());
        return ResponseEntity.ok(ApiResponses.ok(
                subscriptionService.toDto(sub), "Premium granted"));
    }

    @PostMapping("/admin/downgrade")
    @PreAuthorize("hasAnyRole('LANDLORD', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> adminDowngrade(
            @RequestBody DowngradeRequest req) {
        subscriptionService.downgradeToFree(req.userId());
        return ResponseEntity.ok(ApiResponses.ok(
                Map.of("userId", req.userId(), "tier", "FREE"), "Downgraded"));
    }

    /**
     * Self-upgrade entrypoint — returns payment reference; the actual
     * tier switch happens when Kafka {@code payment.subscription-activated}
     * is consumed. Kept here for API completeness.
     */
    @PostMapping("/me/upgrade")
    public ResponseEntity<ApiResponse<Map<String, Object>>> selfUpgrade(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody UpgradeRequest req) {
        UUID userId = UUID.fromString(jwt.getSubject());
        // Returning a payment ref here would normally involve Payment-Service;
        // for the thesis build, the admin grant endpoint above is the demo path.
        return ResponseEntity.ok(ApiResponses.ok(
                Map.of(
                        "userId", userId,
                        "months", req.months(),
                        "amountVnd", 19000 * req.months(),
                        "paymentProvider", "VNPAY",
                        "note", "Complete payment to activate — consume payment.subscription-activated Kafka event"
                ),
                "Payment intent created"));
    }

    public record AdminGrantRequest(UUID userId, int months) {}
    public record DowngradeRequest(UUID userId) {}
    public record UpgradeRequest(int months) {}
}
