package com.smartseason.notification.dispatch;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "smartseason.notifications.mode", havingValue = "mock",
        matchIfMissing = true)
public class LoggingSmsAdapter implements DeliveryAdapter {

    private static final Logger log = LoggerFactory.getLogger(LoggingSmsAdapter.class);

    private final List<String> sent = new CopyOnWriteArrayList<>();

    @Override
    public DeliveryChannel channel() {
        return DeliveryChannel.SMS;
    }

    @Override
    public DeliveryResult send(String destination, String subject, String body) {
        if (destination == null || destination.isBlank()) {
            return DeliveryResult.rejected("no destination");
        }
        if (body != null && body.length() > 1600) {
            return DeliveryResult.rejected("message exceeds the 1600 character SMS limit");
        }

        sent.add(destination + "|" + body);
        log.info("[mock SMS] to {}: {}", destination, body);
        return DeliveryResult.accepted("mock-" + UUID.randomUUID().toString().substring(0, 8));
    }

    public List<String> sent() {
        return new ArrayList<>(sent);
    }

    public void reset() {
        sent.clear();
    }
}
