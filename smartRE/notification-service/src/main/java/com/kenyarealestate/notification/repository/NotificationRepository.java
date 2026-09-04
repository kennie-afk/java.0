package com.kenyarealestate.notification.repository;

import com.kenyarealestate.notification.entity.Channel;
import com.kenyarealestate.notification.entity.Notification;
import com.kenyarealestate.notification.entity.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Optional<Notification> findByDedupKey(String dedupKey);

    Page<Notification> findByUserIdAndChannelOrderByCreatedAtDesc(UUID userId, Channel channel, Pageable pageable);

    long countByUserIdAndChannelAndReadAtIsNull(UUID userId, Channel channel);

    Page<Notification> findByStatusOrderByCreatedAtDesc(NotificationStatus status, Pageable pageable);

    Page<Notification> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("""
           SELECT n FROM Notification n
           WHERE n.status IN (com.kenyarealestate.notification.entity.NotificationStatus.PENDING,
                              com.kenyarealestate.notification.entity.NotificationStatus.FAILED)
             AND n.channel <> com.kenyarealestate.notification.entity.Channel.IN_APP
             AND n.attempts < :maxAttempts
             AND (n.nextAttemptAt IS NULL OR n.nextAttemptAt <= :now)
           ORDER BY n.createdAt ASC
           """)
    List<Notification> findRetryable(@Param("now") LocalDateTime now,
                                     @Param("maxAttempts") int maxAttempts,
                                     Pageable pageable);

    @Modifying
    @Query("""
           UPDATE Notification n SET n.readAt = :now
           WHERE n.userId = :userId AND n.channel = com.kenyarealestate.notification.entity.Channel.IN_APP
             AND n.readAt IS NULL
           """)
    int markAllRead(@Param("userId") UUID userId, @Param("now") LocalDateTime now);
}
