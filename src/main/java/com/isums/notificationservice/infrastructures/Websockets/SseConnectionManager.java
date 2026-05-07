package com.isums.notificationservice.infrastructures.Websockets;

import com.isums.notificationservice.domains.dtos.NotificationDto;
import com.isums.notificationservice.domains.entities.ManagerNotification;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class SseConnectionManager {

    // Must be shorter than the gateway's spring.http.clients.read-timeout (10s).
    // Otherwise the gateway's Apache HttpClient times out the upstream read,
    // closes the connection, and the client has to reconnect.
    private static final long HEARTBEAT_INTERVAL_SECONDS = 7L;
    // Finite emitter timeout forces clients to cycle connections periodically,
    // which lets the upstream proxy / gateway release pool slots held by
    // abandoned streams (dead laptops, closed tabs, reloaded HMR modules).
    private static final long EMITTER_TIMEOUT_MS = 30L * 60L * 1000L;

    private final Map<UUID, CopyOnWriteArrayList<SseEmitter>> emitters
            = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatExecutor =
            Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "notification-sse-heartbeat");
                thread.setDaemon(true);
                return thread;
            });

    @PostConstruct
    void startHeartbeat() {
        heartbeatExecutor.scheduleAtFixedRate(
                this::sendHeartbeats,
                HEARTBEAT_INTERVAL_SECONDS,
                HEARTBEAT_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
    }

    @PreDestroy
    void stopHeartbeat() {
        heartbeatExecutor.shutdown();
        try {
            if (!heartbeatExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                heartbeatExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            heartbeatExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        emitters.values().forEach(list -> list.forEach(SseEmitter::complete));
        emitters.clear();
    }

    public SseEmitter subscribe(UUID recipientId) {
        return subscribe(recipientId, new SseEmitter(EMITTER_TIMEOUT_MS));
    }

    SseEmitter subscribe(UUID recipientId, SseEmitter emitter) {
        emitters.computeIfAbsent(recipientId, k -> new CopyOnWriteArrayList<>())
                .add(emitter);

        Runnable remove = () -> remove(recipientId, emitter);

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
        if (list == null || list.isEmpty()) {
            log.info("[SSE] Push skipped recipientId={} notificationId={} reason=no_active_connection",
                    recipientId, notification.getId());
            return;
        }

        NotificationDto dto = NotificationDto.from(notification);
        int before = list.size();

        list.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event()
                        .id(notification.getId().toString())
                        .name("notification")
                        .data(dto));
            } catch (Exception e) {
                log.warn("[SSE] Push failed recipientId={}: {}", recipientId, e.getMessage());
                remove(recipientId, emitter);
            }
        });
        int after = connectionCount(recipientId);
        log.info("[SSE] Push attempted recipientId={} notificationId={} before={} after={}",
                recipientId, notification.getId(), before, after);
    }

    void sendHeartbeats() {
        emitters.forEach((recipientId, list) -> list.forEach(emitter -> {
            try {
                emitter.send(SseEmitter.event().comment("heartbeat"));
            } catch (Exception e) {
                log.warn("[SSE] Heartbeat failed recipientId={}: {}", recipientId, e.getMessage());
                remove(recipientId, emitter);
            }
        }));
    }

    int connectionCount(UUID recipientId) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(recipientId);
        return list == null ? 0 : list.size();
    }

    private void remove(UUID recipientId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(recipientId);
        if (list == null) return;

        list.remove(emitter);
        if (list.isEmpty()) {
            emitters.remove(recipientId, list);
        }
    }
}
