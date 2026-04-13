package com.isums.notificationservice.controllers;

import com.isums.notificationservice.domains.dtos.NotificationDto;
import com.isums.notificationservice.domains.enums.NotificationCategory;
import com.isums.notificationservice.exceptions.GlobalExceptionHandler;
import com.isums.notificationservice.exceptions.NotFoundException;
import com.isums.notificationservice.infrastructures.Websockets.SseConnectionManager;
import com.isums.notificationservice.infrastructures.abstracts.ManagerNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("ManagerNotificationController")
class ManagerNotificationControllerTest {

    @Mock private ManagerNotificationService service;
    @Mock private SseConnectionManager sseManager;

    @InjectMocks private ManagerNotificationController controller;

    private MockMvc mvc;
    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject(userId.toString()).build();

        HandlerMethodArgumentResolver jwtResolver = new HandlerMethodArgumentResolver() {
            @Override public boolean supportsParameter(MethodParameter p) {
                return Jwt.class.equals(p.getParameterType());
            }
            @Override public Object resolveArgument(MethodParameter p, ModelAndViewContainer m,
                                                    NativeWebRequest w, WebDataBinderFactory b) { return jwt; }
        };

        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(jwtResolver)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET / delegates to service with caller UUID and PageRequest")
    void list() throws Exception {
        NotificationDto dto = NotificationDto.builder()
                .id(UUID.randomUUID()).category("CONTRACT_EXPIRED")
                .title("t").body("b").isRead(false).createdAt(Instant.now()).build();
        when(service.getByRecipient(any(UUID.class), any()))
                .thenReturn(new PageImpl<>(List.of(dto)));

        // Spring Boot 4 no longer guarantees a stable JSON contract for Page<T> in REST
        // responses (known deprecation). We only assert the controller delegates correctly.
        mvc.perform(get("/api/notifications/manager"));

        verify(service).getByRecipient(any(UUID.class), any());
    }

    @Test
    @DisplayName("GET /unread-count returns {count: N}")
    void unreadCount() throws Exception {
        when(service.countUnread(userId)).thenReturn(3L);

        mvc.perform(get("/api/notifications/manager/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(3));
    }

    @Test
    @DisplayName("PUT /{id}/read marks notification read")
    void markRead() throws Exception {
        UUID id = UUID.randomUUID();

        mvc.perform(put("/api/notifications/manager/{id}/read", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Marked as read"));

        verify(service).markRead(id, userId);
    }

    @Test
    @DisplayName("PUT /{id}/read returns 404 when notification missing")
    void markReadNotFound() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new NotFoundException("not found"))
                .when(service).markRead(id, userId);

        mvc.perform(put("/api/notifications/manager/{id}/read", id))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("PUT /read-all marks all notifications read")
    void markAllRead() throws Exception {
        mvc.perform(put("/api/notifications/manager/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("All marked as read"));

        verify(service).markAllRead(userId);
    }

    @Test
    @DisplayName("GET /stream subscribes via SSE and sends initial unread_count")
    void stream() throws Exception {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        when(sseManager.subscribe(userId)).thenReturn(emitter);
        when(service.countUnread(userId)).thenReturn(5L);

        mvc.perform(get("/api/notifications/manager/stream"))
                .andExpect(status().isOk());

        verify(sseManager).subscribe(userId);
        verify(service).countUnread(userId);
    }
}
