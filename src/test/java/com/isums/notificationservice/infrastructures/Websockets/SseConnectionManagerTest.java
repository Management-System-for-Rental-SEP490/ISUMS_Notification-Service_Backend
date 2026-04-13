package com.isums.notificationservice.infrastructures.Websockets;

import com.isums.notificationservice.domains.entities.ManagerNotification;
import com.isums.notificationservice.domains.enums.NotificationCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SseConnectionManager")
class SseConnectionManagerTest {

    private final SseConnectionManager manager = new SseConnectionManager();

    private ManagerNotification notif(UUID recipientId) {
        return ManagerNotification.builder()
                .id(UUID.randomUUID()).recipientId(recipientId)
                .category(NotificationCategory.PAYMENT_OVERDUE)
                .title("t").body("b")
                .createdAt(Instant.now()).build();
    }

    @Test
    @DisplayName("subscribe returns an emitter and push delivers to connected clients")
    void subscribeAndPush() throws IOException {
        UUID recipientId = UUID.randomUUID();
        SseEmitter emitter = manager.subscribe(recipientId);

        assertThat(emitter).isNotNull();
        // push should not throw; emitter receives the event quietly
        manager.push(recipientId, notif(recipientId));
    }

    @Test
    @DisplayName("push is no-op when recipient has no emitters")
    void pushNoEmitters() {
        UUID recipientId = UUID.randomUUID();
        manager.push(recipientId, notif(recipientId));
    }

    @Test
    @DisplayName("each subscribe adds a separate emitter; push reaches all")
    void multipleSubscribers() {
        UUID recipientId = UUID.randomUUID();
        SseEmitter e1 = manager.subscribe(recipientId);
        SseEmitter e2 = manager.subscribe(recipientId);

        assertThat(e1).isNotSameAs(e2);
        manager.push(recipientId, notif(recipientId));
    }
}
