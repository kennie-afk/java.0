package com.hms.notify;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Runs the dispatcher on a timer. Switched off in tests (hms.notifications.dispatcher=false) so they drive it by hand. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "hms.notifications.dispatcher", havingValue = "true", matchIfMissing = true)
class NotificationScheduler {

    private final NotificationDispatcher dispatcher;

    NotificationScheduler(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelayString = "${hms.notifications.poll-ms:5000}", initialDelayString = "${hms.notifications.poll-ms:5000}")
    void tick() {
        try {
            dispatcher.dispatchOnce();
        } catch (RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger("hms.notifications").error("dispatch pass failed", e);
        }
    }
}
