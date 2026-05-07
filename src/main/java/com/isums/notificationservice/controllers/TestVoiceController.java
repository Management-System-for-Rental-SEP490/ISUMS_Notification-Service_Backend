package com.isums.notificationservice.controllers;

import com.isums.notificationservice.domains.dtos.AlertDispatchRequest;
import com.isums.notificationservice.domains.dtos.AlertDispatchResponse;
import com.isums.notificationservice.domains.dtos.ApiResponse;
import com.isums.notificationservice.domains.dtos.ApiResponses;
import com.isums.notificationservice.domains.enums.AlertEventType;
import com.isums.notificationservice.infrastructures.grpcs.UserGrpcClient;
import com.isums.notificationservice.services.NotificationDispatchService;
import com.isums.notificationservice.services.NotificationQuotaService;
import com.isums.userservice.grpc.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Self-serve test call: triggers a GAS_CRITICAL alert for the signed-in
 * user. Rate-limited to 1 per day (Redis key) so QA / thesis demos don't
 * burn an arbitrary amount of credit. Still counts against the monthly
 * quota like any other dispatch.
 */
@RestController
@RequestMapping("/api/notifications/preferences/me")
@RequiredArgsConstructor
@Slf4j
public class TestVoiceController {

    private final NotificationDispatchService dispatchService;
    private final UserGrpcClient userGrpcClient;
    private final StringRedisTemplate redis;

    @PostMapping("/test-voice")
    public ResponseEntity<ApiResponse<AlertDispatchResponse>> testVoice(
            @AuthenticationPrincipal Jwt jwt) {
        UUID userId = UUID.fromString(jwt.getSubject());

        String key = "notif:test-voice:daily:" + userId;
        Boolean firstToday = redis.opsForValue().setIfAbsent(key, "1", Duration.ofDays(1));
        if (!Boolean.TRUE.equals(firstToday)) {
            Long ttl = redis.getExpire(key);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(
                    ApiResponses.fail(HttpStatus.TOO_MANY_REQUESTS,
                            "Test call already used today. Next available in " + ttl + "s."));
        }

        // Pull mainHouseId from user-service so escalation can resolve the
        // region's manager when the user presses DTMF=2. Without it, the
        // test call would dial the tenant successfully but escalation
        // resolves to null (no houseId → no region → no manager) — which
        // is fine for "did my phone ring" but breaks the "press 2 to
        // forward" demo path.
        String houseId = null;
        try {
            UserResponse u = userGrpcClient.getUserByKeycloakId(userId.toString());
            String mainHouseId = readOptionalString(u, "getMainHouseId");
            if (mainHouseId != null && !mainHouseId.isBlank()) {
                houseId = mainHouseId;
            }
        } catch (Exception e) {
            log.warn("[TestVoice] mainHouseId lookup failed userId={}: {}", userId, e.getMessage());
        }

        AlertDispatchRequest req = new AlertDispatchRequest(
                userId,
                "test-" + UUID.randomUUID(),
                AlertEventType.GAS_CRITICAL,
                houseId, null, "Test Area",
                "test-thing",
                "gas_ppm", 325.0, "ppm",
                Map.of("testMode", true)
        );

        AlertDispatchResponse resp = dispatchService.dispatch(req);
        return ResponseEntity.ok(ApiResponses.ok(resp, "Test dispatch triggered"));
    }

    private static String readOptionalString(Object target, String getterName) {
        if (target == null) {
            return null;
        }
        try {
            Object value = target.getClass().getMethod(getterName).invoke(target);
            return value instanceof String s ? s : null;
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }
}
