package com.isums.notificationservice.controllers;

import com.isums.notificationservice.domains.dtos.ApiResponse;
import com.isums.notificationservice.domains.dtos.ApiResponses;
import com.isums.notificationservice.domains.dtos.NotificationPreferencesDto;
import com.isums.notificationservice.domains.dtos.SubscriptionDto;
import com.isums.notificationservice.domains.dtos.UpdatePreferencesRequest;
import com.isums.notificationservice.domains.entities.UserNotificationPreferences;
import com.isums.notificationservice.services.NotificationPreferenceService;
import com.isums.notificationservice.services.NotificationQuotaService;
import com.isums.notificationservice.services.NotificationSubscriptionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications/preferences")
@RequiredArgsConstructor
public class NotificationPreferencesController {

    private final NotificationPreferenceService preferenceService;
    private final NotificationSubscriptionService subscriptionService;
    private final NotificationQuotaService quotaService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<NotificationPreferencesDto>> getMyPreferences(
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        UserNotificationPreferences p = preferenceService.getOrCreate(userId);
        return ResponseEntity.ok(ApiResponses.ok(preferenceService.toDto(p), "OK"));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<NotificationPreferencesDto>> updateMyPreferences(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdatePreferencesRequest req,
            HttpServletRequest httpReq) {
        UUID userId = UUID.fromString(jwt.getSubject());
        // Tier check (PREMIUM gate on voice / SMS) only applies to TENANT.
        // Landlord & manager voice are allowed by role, not subscription.
        boolean tierExempt = hasAnyRealmRole(jwt, "LANDLORD", "MANAGER");
        // PDPL audit metadata — must be captured at request boundary
        // because the service layer doesn't see the Servlet API directly.
        String clientIp = resolveClientIp(httpReq);
        String userAgent = httpReq.getHeader("User-Agent");
        UserNotificationPreferences p = preferenceService.update(
                userId, req, tierExempt, clientIp, userAgent);
        return ResponseEntity.ok(ApiResponses.ok(preferenceService.toDto(p), "Updated"));
    }

    /**
     * Trust X-Forwarded-For only if the request transited Cloudflare
     * (its CF-Connecting-IP is the real client). For local dev hits we
     * fall back to the Servlet remote address.
     */
    private static String resolveClientIp(HttpServletRequest req) {
        String cf = req.getHeader("CF-Connecting-IP");
        if (cf != null && !cf.isBlank()) return cf.trim();
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
        return req.getRemoteAddr();
    }

    @SuppressWarnings("unchecked")
    private static boolean hasAnyRealmRole(Jwt jwt, String... roles) {
        Object realmAccess = jwt.getClaims().get("realm_access");
        if (!(realmAccess instanceof Map<?, ?> map)) return false;
        Object rolesObj = map.get("roles");
        if (!(rolesObj instanceof java.util.Collection<?> col)) return false;
        for (Object r : col) {
            for (String want : roles) {
                if (want.equalsIgnoreCase(String.valueOf(r))) return true;
            }
        }
        return false;
    }

    @GetMapping("/me/subscription")
    public ResponseEntity<ApiResponse<SubscriptionDto>> getMySubscription(
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        SubscriptionDto dto = subscriptionService.toDto(
                preferenceService.getSubscriptionOrCreate(userId));
        return ResponseEntity.ok(ApiResponses.ok(dto, "OK"));
    }

    @GetMapping("/me/quota")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMyQuota(
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        SubscriptionDto sub = subscriptionService.toDto(
                preferenceService.getSubscriptionOrCreate(userId));
        long cooldown = quotaService.remainingRateLimitSec(userId);
        Map<String, Object> body = Map.of(
                "tier", sub.tier(),
                "voiceQuotaMonthly", sub.voiceQuotaMonthly(),
                "voiceUsedThisMonth", sub.voiceUsedThisMonth(),
                "voiceRemaining", sub.voiceRemaining(),
                "smsQuotaMonthly", sub.smsQuotaMonthly(),
                "smsUsedThisMonth", sub.smsUsedThisMonth(),
                "smsRemaining", sub.smsRemaining(),
                "voiceRateLimitRemainingSec", cooldown,
                "currentMonthKey", NotificationQuotaService.currentMonthKey()
        );
        return ResponseEntity.ok(ApiResponses.ok(body, "OK"));
    }
}
