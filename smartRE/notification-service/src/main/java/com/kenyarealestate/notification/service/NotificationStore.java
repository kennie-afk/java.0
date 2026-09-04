package com.kenyarealestate.notification.service;

import com.kenyarealestate.notification.entity.Notification;
import com.kenyarealestate.notification.entity.NotificationStatus;
import com.kenyarealestate.notification.repository.NotificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
public class NotificationStore {

    private final NotificationRepository repo;

    public NotificationStore(NotificationRepository repo) {
        this.repo = repo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Notification> createIfAbsent(Notification n) {
        try {
            return Optional.of(repo.saveAndFlush(n));
        } catch (DataIntegrityViolationException e) {
            log.debug("Duplicate notification suppressed: templateCode={} dedupKey={}",
                    n.getTemplateCode(), n.getDedupKey());
            return Optional.empty();
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(Notification n) {
        n.setStatus(NotificationStatus.SENT);
        n.setSentAt(LocalDateTime.now());
        n.setNextAttemptAt(null);
        n.setLastError(null);
        repo.save(n);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Notification n, String error, int maxAttempts) {
        n.setAttempts(n.getAttempts() + 1);
        n.setLastError(error);
        n.setStatus(NotificationStatus.FAILED);
        if (n.getAttempts() < maxAttempts) {
            long minutes = Math.min(60L, 1L << Math.min(n.getAttempts() - 1, 6));
            n.setNextAttemptAt(LocalDateTime.now().plusMinutes(minutes));
        } else {
            n.setNextAttemptAt(null);
            log.error("ALERT: notification {} to user {} gave up after {} attempts: {}",
                    n.getId(), n.getUserId(), n.getAttempts(), error);
        }
        repo.save(n);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deferBriefly(Notification n) {
        n.setStatus(NotificationStatus.PENDING);
        n.setNextAttemptAt(LocalDateTime.now().plusMinutes(1));
        repo.save(n);
    }
}
