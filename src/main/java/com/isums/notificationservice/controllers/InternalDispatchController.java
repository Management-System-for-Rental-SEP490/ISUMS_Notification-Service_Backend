package com.isums.notificationservice.controllers;

import com.isums.notificationservice.domains.dtos.AlertDispatchRequest;
import com.isums.notificationservice.domains.dtos.AlertDispatchResponse;
import com.isums.notificationservice.domains.dtos.ApiResponse;
import com.isums.notificationservice.domains.dtos.ApiResponses;
import com.isums.notificationservice.services.NotificationDispatchService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Called from the AWS Lambda tier (esp32-threshold-checker + esp32-eif-score)
 * after an IoT alert crosses a threshold. Skips JWT auth because Lambda runs
 * under AWS IAM, not Keycloak — uses a shared X-Internal-Key instead.
 *
 * <p>Also usable from curl/Postman in dev by passing the header manually.
 */
@RestController
@RequestMapping("/api/notifications/internal")
@RequiredArgsConstructor
@Slf4j
public class InternalDispatchController {

    private final NotificationDispatchService dispatchService;

    @Value("${app.notification.internal.api-key:}")
    private String internalApiKey;

    @PostMapping("/dispatch")
    public ResponseEntity<ApiResponse<AlertDispatchResponse>> dispatch(
            HttpServletRequest request,
            @RequestBody AlertDispatchRequest req) {
        if (!isAuthorised(request)) {
            log.warn("[InternalDispatch] unauthorised POST from {}", request.getRemoteAddr());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponses.fail(HttpStatus.UNAUTHORIZED, "Invalid internal key"));
        }

        log.info("[InternalDispatch] userId={} event={} alertId={}",
                req.userId(), req.eventType(), req.alertId());

        AlertDispatchResponse resp = dispatchService.dispatch(req);
        return ResponseEntity.ok(ApiResponses.ok(resp, "Dispatched"));
    }

    private boolean isAuthorised(HttpServletRequest request) {
        if (internalApiKey == null || internalApiKey.isBlank()) {
            log.error("[InternalDispatch] app.notification.internal.api-key not configured — rejecting all requests");
            return false;
        }
        String header = request.getHeader("X-Internal-Key");
        return internalApiKey.equals(header);
    }
}
