package com.boxy.boxy.modules.notifications.service;

import com.boxy.boxy.core.exception.ResourceNotFoundException;
import com.boxy.boxy.core.realtime.RealtimeMessage;
import com.boxy.boxy.core.security.UserPrincipal;
import com.boxy.boxy.modules.notifications.dto.NotificationDto;
import com.boxy.boxy.modules.notifications.entity.Notification;
import com.boxy.boxy.modules.notifications.repository.NotificationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final long ME = 7L;

    @Mock private NotificationRepository notificationRepository;
    @Mock private SimpMessagingTemplate messagingTemplate;
    @Mock private PlatformTransactionManager transactionManager;

    @InjectMocks private NotificationService service;

    @BeforeEach
    void authenticateAsMe() {
        UserPrincipal principal = UserPrincipal.create(ME, 4L, "ana", "ana@boxy.dev", "hash", "Ana", 1L, "ACTIVE", List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private static NotificationService.Spec spec() {
        return new NotificationService.Spec("LOW_STOCK", "warning", "Stock bajo", "Quedan 3.", "Producto", "5", "/inventory/products/5");
    }

    private static Notification stored(long id, long userId, Instant readAt) {
        return Notification.builder().id(id).companyId(4L).recipientUserId(userId).type("LOW_STOCK").severity("warning")
                .title("Stock bajo").message("Quedan 3.").readAt(readAt).createdAt(Instant.parse("2026-01-01T10:00:00Z")).build();
    }

    // ---- creating and delivering ----

    @Test
    void storesOneRowPerRecipientThenPushesEachToTheirOwnQueue() {
        when(notificationRepository.saveAll(any())).thenAnswer(inv -> {
            List<Notification> rows = (List<Notification>) inv.getArgument(0);
            long id = 100;
            for (Notification row : rows) {
                row.setId(id++);
            }
            return rows;
        });

        service.notifyUsers(4L, Set.of(21L, 22L), spec());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Notification>> saved = ArgumentCaptor.forClass(List.class);
        InOrder order = Mockito.inOrder(notificationRepository, messagingTemplate);
        order.verify(notificationRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).extracting(Notification::getRecipientUserId).containsExactlyInAnyOrder(21L, 22L);
        assertThat(saved.getValue()).allSatisfy(n -> {
            assertThat(n.getCompanyId()).isEqualTo(4L);
            assertThat(n.getType()).isEqualTo("LOW_STOCK");
            assertThat(n.getReadAt()).isNull();
        });
        // Pushed only after the rows exist (in that order), each addressed by user id — the
        // WebSocket principal's name — so a client can mark it read as soon as it arrives.
        order.verify(messagingTemplate, Mockito.times(2))
                .convertAndSendToUser(anyString(), eq("/queue/notifications"), any(RealtimeMessage.class));
        verify(messagingTemplate).convertAndSendToUser(eq("21"), eq("/queue/notifications"), any(RealtimeMessage.class));
        verify(messagingTemplate).convertAndSendToUser(eq("22"), eq("/queue/notifications"), any(RealtimeMessage.class));
    }

    @Test
    void doesNothingWhenThereIsNobodyToTell() {
        service.notifyUsers(4L, Set.of(), spec());
        service.notifyUsers(4L, null, spec());

        verifyNoInteractions(notificationRepository, messagingTemplate);
    }

    @Test
    void overlongTextIsTruncatedToTheColumnSizeInsteadOfFailingTheInsert() {
        when(notificationRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        service.notifyUsers(4L, Set.of(21L), new NotificationService.Spec(
                "LOW_STOCK", "warning", "T".repeat(400), "M".repeat(900), null, null, null));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Notification>> saved = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(saved.capture());
        assertThat(saved.getValue().get(0).getTitle()).hasSize(150).endsWith("…");
        assertThat(saved.getValue().get(0).getMessage()).hasSize(500).endsWith("…");
    }

    @Test
    void aFailureToStoreIsSwallowedAndNothingIsPushed() {
        when(notificationRepository.saveAll(any())).thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> service.notifyUsers(4L, Set.of(21L), spec())).doesNotThrowAnyException();
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void aFailureToPushDoesNotLoseTheStoredNotification() {
        when(notificationRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));
        doThrow(new IllegalStateException("broker gone")).when(messagingTemplate)
                .convertAndSendToUser(anyString(), anyString(), any());

        assertThatCode(() -> service.notifyUsers(4L, Set.of(21L), spec())).doesNotThrowAnyException();
        verify(notificationRepository).saveAll(any());
    }

    // ---- reading and managing my own ----

    @Test
    void listsOnlyMyNotifications() {
        PageRequest page = PageRequest.of(0, 20);
        when(notificationRepository.findByRecipientUserIdOrderByCreatedAtDesc(ME, page))
                .thenReturn(new PageImpl<>(List.of(stored(1, ME, null))));

        var result = service.list(false, page);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).isRead()).isFalse();
        assertThat(result.getContent().get(0).getCreatedAt()).isEqualTo("2026-01-01T10:00:00Z");
    }

    @Test
    void theUnreadOnlyListUsesTheUnreadQuery() {
        PageRequest page = PageRequest.of(0, 20);
        when(notificationRepository.findByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc(ME, page))
                .thenReturn(new PageImpl<>(List.of()));

        service.list(true, page);

        verify(notificationRepository, never()).findByRecipientUserIdOrderByCreatedAtDesc(any(), any());
    }

    @Test
    void countsMyUnread() {
        when(notificationRepository.countByRecipientUserIdAndReadAtIsNull(ME)).thenReturn(3L);

        assertThat(service.unreadCount()).isEqualTo(3L);
    }

    @Test
    void markingReadSetsTheTimestamp() {
        Notification unread = stored(1, ME, null);
        when(notificationRepository.findByIdAndRecipientUserId(1L, ME)).thenReturn(Optional.of(unread));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        NotificationDto dto = service.markRead(1L);

        assertThat(dto.isRead()).isTrue();
        assertThat(unread.getReadAt()).isNotNull();
    }

    @Test
    void markingAnAlreadyReadNotificationKeepsTheOriginalTimestamp() {
        Instant firstRead = Instant.parse("2026-01-02T00:00:00Z");
        Notification read = stored(1, ME, firstRead);
        when(notificationRepository.findByIdAndRecipientUserId(1L, ME)).thenReturn(Optional.of(read));
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        service.markRead(1L);

        assertThat(read.getReadAt()).isEqualTo(firstRead);
    }

    @Test
    void someoneElsesNotificationIsAnUnknownNotificationNotAForbiddenOne() {
        when(notificationRepository.findByIdAndRecipientUserId(99L, ME)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(99L)).isInstanceOf(ResourceNotFoundException.class);
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markAllReadOnlyTouchesMyRows() {
        when(notificationRepository.markAllRead(eq(ME), any(Instant.class))).thenReturn(4);

        assertThat(service.markAllRead()).isEqualTo(4);
    }

    @Test
    void purgeDeletesOnlyReadNotificationsOlderThanTheRetention() {
        when(notificationRepository.deleteReadBefore(any(Instant.class))).thenReturn(12);

        assertThat(service.purgeReadOlderThan(Duration.ofDays(90))).isEqualTo(12);

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(notificationRepository).deleteReadBefore(cutoff.capture());
        assertThat(cutoff.getValue()).isBefore(Instant.now().minus(Duration.ofDays(89)));
    }
}
