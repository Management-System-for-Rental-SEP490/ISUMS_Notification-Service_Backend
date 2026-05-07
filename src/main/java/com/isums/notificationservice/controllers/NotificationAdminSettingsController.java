package com.isums.notificationservice.controllers;

import com.isums.notificationservice.domains.dtos.ApiResponse;
import com.isums.notificationservice.domains.dtos.ApiResponses;
import com.isums.notificationservice.services.NotificationRuntimeSettingService;
import com.isums.notificationservice.services.VoiceProviderRouter;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications/admin/settings")
@RequiredArgsConstructor
public class NotificationAdminSettingsController {

    private final NotificationRuntimeSettingService settingService;
    private final VoiceProviderRouter voiceRouter;

    @GetMapping("/voice-provider")
    @PreAuthorize("hasAnyRole('LANDLORD', 'SYSTEM_ADMIN', 'ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getVoiceProvider() {
        String active = voiceRouter.activeProviderId();
        List<String> available = voiceRouter.availableProviderIds();
        return ResponseEntity.ok(ApiResponses.ok(
                Map.of("active", active, "available", available),
                "OK"));
    }

    @PutMapping("/voice-provider")
    @PreAuthorize("hasAnyRole('LANDLORD', 'SYSTEM_ADMIN', 'ADMIN', 'MANAGER')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> setVoiceProvider(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody VoiceProviderUpdateRequest req) {
        String desired = req.provider() == null ? "" : req.provider().trim().toUpperCase();
        List<String> available = voiceRouter.availableProviderIds();
        if (!available.contains(desired)) {
            return ResponseEntity.badRequest().body(ApiResponses.fail(
                    HttpStatus.BAD_REQUEST,
                    "Unknown provider: " + desired + ". Available: " + available));
        }
        UUID actor = UUID.fromString(jwt.getSubject());
        settingService.set(NotificationRuntimeSettingService.KEY_VOICE_PROVIDER, desired, actor);
        return ResponseEntity.ok(ApiResponses.ok(
                Map.of("active", desired, "available", available),
                "Voice provider updated"));
    }

    public record VoiceProviderUpdateRequest(@NotBlank String provider) {}
}
