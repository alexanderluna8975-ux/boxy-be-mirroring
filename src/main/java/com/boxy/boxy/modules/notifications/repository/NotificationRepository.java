package com.boxy.boxy.modules.notifications.repository;

import com.boxy.boxy.modules.notifications.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/** Every read/write is scoped by {@code recipientUserId}: a user can only ever reach their own rows. */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByRecipientUserIdOrderByCreatedAtDesc(Long recipientUserId, Pageable pageable);

    Page<Notification> findByRecipientUserIdAndReadAtIsNullOrderByCreatedAtDesc(Long recipientUserId, Pageable pageable);

    long countByRecipientUserIdAndReadAtIsNull(Long recipientUserId);

    Optional<Notification> findByIdAndRecipientUserId(Long id, Long recipientUserId);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = :now WHERE n.recipientUserId = :userId AND n.readAt IS NULL")
    int markAllRead(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.readAt IS NOT NULL AND n.readAt < :cutoff")
    int deleteReadBefore(@Param("cutoff") Instant cutoff);
}
