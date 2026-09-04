package com.kenyarealestate.notification.service;

import com.kenyarealestate.notification.entity.Notification;
import com.kenyarealestate.notification.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class NotificationRetryJob {

    private static final int BATCH_SIZE = 100;

    private final NotificationRepository repo;
    private final NotificationDispatcher dispatcher;
    private final int maxAttempts;

    public NotificationRetryJob(NotificationRepository repo,
                                NotificationDispatcher dispatcher,
                                @Value("${notification.max-attempts:5}") int maxAttempts) {
        this.repo = repo;
        this.dispatcher = dispatcher;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${notification.retry.fixed-delay-ms:60000}",
               initialDelayString = "${notification.retry.initial-delay-ms:30000}")
    public void sweep() {
        List<Notification> due = repo.findRetryable(LocalDateTime.now(), maxAttempts, PageRequest.of(0, BATCH_SIZE));
        if (due.isEmpty()) return;

        log.info("Retrying {} pending/failed notification(s)", due.size());
        for (Notification n : due) {
            try {
                dispatcher.attemptDelivery(n);
            } catch (Exception e) {
                log.error("Retry sweep failed for notification {}: {}", n.getId(), e.getMessage(), e);
            }
        }
    }
}
