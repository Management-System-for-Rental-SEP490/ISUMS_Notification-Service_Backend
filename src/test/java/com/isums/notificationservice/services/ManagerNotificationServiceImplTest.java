package com.isums.notificationservice.services;

import com.isums.notificationservice.domains.dtos.NotificationDto;
import com.isums.notificationservice.domains.entities.ManagerNotification;
import com.isums.notificationservice.domains.enums.NotificationCategory;
import com.isums.notificationservice.exceptions.NotFoundException;
import com.isums.notificationservice.infrastructures.Websockets.SseConnectionManager;
import com.isums.notificationservice.infrastructures.repositories.ManagerNotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ManagerNotificationServiceImpl")
class ManagerNotificationServiceImplTest {

    @Mock private ManagerNotificationRepository repo;
    @Mock private SseConnectionManager sseManager;

    @InjectMocks private ManagerNotificationServiceImpl service;

    private UUID recipientId;

    @BeforeEach
    void setUp() {
        recipientId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("send")
    class Send {

        @Test
        @DisplayName("persists notification and pushes SSE")
        void sends() {
            service.send(recipientId, NotificationCategory.PAYMENT_OVERDUE,
                    "Title", "Body", "/action", Map.of("k", "v"));

            ArgumentCaptor<ManagerNotification> cap = ArgumentCaptor.forClass(ManagerNotification.class);
            verify(repo).save(cap.capture());
            ManagerNotification saved = cap.getValue();
            assertThat(saved.getRecipientId()).isEqualTo(recipientId);
            assertThat(saved.getTitle()).isEqualTo("Title");
            assertThat(saved.getCategory()).isEqualTo(NotificationCategory.PAYMENT_OVERDUE);
            assertThat(saved.isRead()).isFalse();

            verify(sseManager).push(recipientId, saved);
        }
    }

    @Nested
    @DisplayName("getByRecipient")
    class GetByRecipient {

        @Test
        @DisplayName("maps entities to DTOs, desc by createdAt")
        void returnsPage() {
            ManagerNotification n = ManagerNotification.builder()
                    .id(UUID.randomUUID()).recipientId(recipientId)
                    .category(NotificationCategory.INSPECTION_DONE)
                    .title("t").body("b").createdAt(Instant.now()).build();
            Page<ManagerNotification> page = new PageImpl<>(List.of(n));
            when(repo.findByRecipientIdOrderByCreatedAtDesc(any(UUID.class), any()))
                    .thenReturn(page);

            Page<NotificationDto> res = service.getByRecipient(recipientId, PageRequest.of(0, 10));
            assertThat(res.getContent()).hasSize(1);
            assertThat(res.getContent().get(0).getCategory()).isEqualTo("INSPECTION_DONE");
        }
    }

    @Nested
    @DisplayName("countUnread")
    class CountUnread {

        @Test
        @DisplayName("delegates to repo")
        void delegates() {
            when(repo.countByRecipientIdAndIsReadFalse(recipientId)).thenReturn(7L);
            assertThat(service.countUnread(recipientId)).isEqualTo(7L);
        }
    }

    @Nested
    @DisplayName("markRead")
    class MarkRead {

        @Test
        @DisplayName("sets isRead and readAt and saves")
        void marks() {
            UUID notifId = UUID.randomUUID();
            ManagerNotification n = ManagerNotification.builder()
                    .id(notifId).recipientId(recipientId)
                    .category(NotificationCategory.CONTRACT_EXPIRED)
                    .title("t").body("b").isRead(false).build();
            when(repo.findByIdAndRecipientId(notifId, recipientId)).thenReturn(Optional.of(n));

            service.markRead(notifId, recipientId);

            assertThat(n.isRead()).isTrue();
            assertThat(n.getReadAt()).isNotNull();
            verify(repo).save(n);
        }

        @Test
        @DisplayName("throws NotFoundException when notification missing")
        void missing() {
            UUID notifId = UUID.randomUUID();
            when(repo.findByIdAndRecipientId(notifId, recipientId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.markRead(notifId, recipientId))
                    .isInstanceOf(NotFoundException.class);
        }
    }

    @Nested
    @DisplayName("markAllRead")
    class MarkAllRead {

        @Test
        @DisplayName("delegates to repo with current timestamp")
        void delegates() {
            service.markAllRead(recipientId);
            verify(repo).markAllReadByRecipientId(any(UUID.class), any(Instant.class));
        }
    }
}
