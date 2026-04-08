package com.isums.notificationservice.infrastructures.Websockets;

import com.isums.notificationservice.domains.dtos.NotificationDto;
import com.isums.notificationservice.domains.entities.ManagerNotification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@Slf4j
public class SseConnectionManager {

    private final Map<UUID, CopyOnWriteArrayList<SseEmitter>> emitters
            = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID recipientId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        emitters.computeIfAbsent(recipientId, k -> new CopyOnWriteArrayList<>())
                .add(emitter);

        Runnable remove = () -> {
            CopyOnWriteArrayList<SseEmitter> list = emitters.get(recipientId);
            if (list != null) list.remove(emitter);
        };

        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());

        log.info("[SSE] Subscribed recipientId={} total={}",
                recipientId, emitters.getOrDefault(recipientId,
                        new CopyOnWriteArrayList<>()).size());
        return emitter;
    }

    public void push(UUID recipientId, ManagerNotification notification) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(recipientId);
        if (list == null || list.isEmpty()) return;

        NotificationDto dto = NotificationDto.from(notification);

        list.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event()
                        .id(notification.getId().toString())
                        .name("notification")
                        .data(dto));
            } catch (Exception e) {
                log.warn("[SSE] Push failed recipientId={}: {}", recipientId, e.getMessage());
                list.remove(emitter);
            }
        });
    }
}
