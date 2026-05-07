package com.isums.notificationservice.controllers;

import com.isums.notificationservice.domains.dtos.ApiResponse;
import com.isums.notificationservice.domains.dtos.ApiResponses;
import com.isums.notificationservice.domains.dtos.SubscriptionPlanDto;
import com.isums.notificationservice.domains.dtos.UpsertSubscriptionPlanRequest;
import com.isums.notificationservice.services.SubscriptionPlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Subscription plans REST. Reads are public-ish (any authenticated
 * user — tenants need this to render the upgrade picker); writes are
 * landlord/admin only.
 */
@RestController
@RequestMapping("/api/notifications/subscriptions/plans")
@RequiredArgsConstructor
public class SubscriptionPlanController {

    private final SubscriptionPlanService planService;

    /** Customer-facing list — only active plans, sorted by sort_order. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<SubscriptionPlanDto>>> list() {
        return ResponseEntity.ok(ApiResponses.ok(
                planService.listActiveForCustomers(), "OK"));
    }

    /** Admin / landlord catalogue view — includes deactivated rows. */
    @GetMapping("/admin")
    @PreAuthorize("hasAnyRole('LANDLORD', 'ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<List<SubscriptionPlanDto>>> listAdmin() {
        return ResponseEntity.ok(ApiResponses.ok(
                planService.listAllForAdmin(), "OK"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<SubscriptionPlanDto>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponses.ok(planService.getById(id), "OK"));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('LANDLORD', 'ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<SubscriptionPlanDto>> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpsertSubscriptionPlanRequest req) {
        UUID actor = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(ApiResponses.ok(
                planService.create(req, actor), "Plan created"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('LANDLORD', 'ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<SubscriptionPlanDto>> update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id,
            @Valid @RequestBody UpsertSubscriptionPlanRequest req) {
        UUID actor = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(ApiResponses.ok(
                planService.update(id, req, actor), "Plan updated"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('LANDLORD', 'ADMIN', 'SYSTEM_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivate(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id) {
        UUID actor = UUID.fromString(jwt.getSubject());
        planService.deactivate(id, actor);
        return ResponseEntity.ok(ApiResponses.ok(null, "Plan deactivated"));
    }
}
