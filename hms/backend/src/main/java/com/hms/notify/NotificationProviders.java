package com.hms.notify;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * hms.notifications.mode = mock (default) or live. Mock logs a masked line and keeps the last few messages in memory for tests; nothing is sent.
 * Live is intentionally not implemented: no provider's API has been assumed, so it fails loudly instead of pretending to deliver.
 */
@Configuration
class NotificationProviders {

    private static final Logger log = LoggerFactory.getLogger("hms.notifications");

    /** The mock keeps a bounded window of what it "sent", so tests can read the wording and nothing grows without limit. */
    static final class MockProvider implements NotificationProvider {
        private final String channel;
        private final Deque<Outbound> recent = new ArrayDeque<>();

        MockProvider(String channel) {
            this.channel = channel;
        }

        @Override
        public String channel() {
            return channel;
        }

        @Override
        public boolean isMock() {
            return true;
        }

        @Override
        public void send(Outbound m) {
            log.info("MOCK {} to {} (nothing was sent): {}", channel, mask(m.recipient()), m.subject());
            synchronized (recent) {
                recent.addLast(m);
                while (recent.size() > 50) {
                    recent.removeFirst();
                }
            }
        }

        List<Outbound> recent() {
            synchronized (recent) {
                return List.copyOf(recent);
            }
        }
    }

    static final class UnconfiguredProvider implements NotificationProvider {
        private final String channel;

        UnconfiguredProvider(String channel) {
            this.channel = channel;
        }

        @Override
        public String channel() {
            return channel;
        }

        @Override
        public boolean isMock() {
            return false;
        }

        @Override
        public void send(Outbound m) throws DeliveryException {
            throw new DeliveryException("Live " + channel + " delivery is not implemented in this build. It needs a provider account and a verified integration; "
                    + "run with HMS_NOTIFICATIONS_MODE=mock to simulate.", true);
        }
    }

    @Bean
    NotificationProvider emailProvider(@Value("${hms.notifications.mode:mock}") String mode) {
        return "live".equalsIgnoreCase(mode) ? new UnconfiguredProvider("EMAIL") : new MockProvider("EMAIL");
    }

    @Bean
    NotificationProvider smsProvider(@Value("${hms.notifications.mode:mock}") String mode) {
        return "live".equalsIgnoreCase(mode) ? new UnconfiguredProvider("SMS") : new MockProvider("SMS");
    }

    /** Enough to recognise a number or address without putting it in a log. */
    static String mask(String recipient) {
        if (recipient == null || recipient.length() < 4) {
            return "***";
        }
        int at = recipient.indexOf('@');
        if (at > 0) {
            return recipient.charAt(0) + "***" + recipient.substring(at);
        }
        return recipient.substring(0, Math.min(4, recipient.length() - 3)) + "•••" + recipient.substring(recipient.length() - 3);
    }
}
