package com.isums.notificationservice.controllers;

import com.isums.notificationservice.domains.dtos.ApiResponse;
import com.isums.notificationservice.domains.dtos.ApiResponses;
import com.isums.notificationservice.domains.dtos.NotificationDto;
import com.isums.notificationservice.infrastructures.Websockets.SseConnectionManager;
import com.isums.notificationservice.infrastructures.abstracts.ManagerNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications/manager")
@RequiredArgsConstructor
public class ManagerNotificationController {

    private final ManagerNotificationService service;
    private final SseConnectionManager sseManager;

    // SSE endpoint — web connect 1 lần khi login
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
//    @PreAuthorize("hasAnyRole('MANAGER', 'LANDLORD')")
    public SseEmitter stream(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        SseEmitter emitter = sseManager.subscribe(userId);

        // Gửi unread count ngay khi connect
        try {
            emitter.send(SseEmitter.event()
                    .name("unread_count")
                    .data(Map.of("count", service.countUnread(userId))));
        } catch (Exception ignored) {}

        return emitter;
    }

    @GetMapping
//    @PreAuthorize("hasAnyRole('MANAGER', 'LANDLORD')")
    public ApiResponse<Page<NotificationDto>> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ApiResponses.ok(service.getByRecipient(userId, PageRequest.of(page, size)), "Success");
    }

    @GetMapping("/unread-count")
//    @PreAuthorize("hasAnyRole('MANAGER', 'LANDLORD')")
    public ApiResponse<Map<String, Long>> unreadCount(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ApiResponses.ok(
                Map.of("count", service.countUnread(userId)),
                "Success");
    }

    @PutMapping("/{id}/read")
//    @PreAuthorize("hasAnyRole('MANAGER', 'LANDLORD')")
    public ApiResponse<Void> markRead(
            @PathVariable UUID id,
            @AuthenticationPrincipal Jwt jwt) {
        service.markRead(id, UUID.fromString(jwt.getSubject()));
        return ApiResponses.ok(null, "Marked as read");
    }

    @PutMapping("/read-all")
//    @PreAuthorize("hasAnyRole('MANAGER', 'LANDLORD')")
    public ApiResponse<Void> markAllRead(@AuthenticationPrincipal Jwt jwt) {
        service.markAllRead(UUID.fromString(jwt.getSubject()));
        return ApiResponses.ok(null, "All marked as read");
    }
}
