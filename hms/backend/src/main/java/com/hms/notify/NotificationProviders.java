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
 * Live uses Africa's Talking (SMS) and SMTP (e-mail) where configured; a channel left unconfigured fails loudly instead of pretending to deliver.
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
            throw new DeliveryException("Live " + channel + " delivery is not configured. Set the provider settings (HMS_SMS_AT_USERNAME and HMS_SMS_AT_API_KEY, or HMS_SMTP_HOST and HMS_SMTP_FROM), "
                    + "or run with HMS_NOTIFICATIONS_MODE=mock to simulate.", true);
        }
    }

    /**
     * mode=live picks the real providers when they are configured: SMS through Africa's Talking (HMS_SMS_AT_USERNAME and HMS_SMS_AT_API_KEY),
     * e-mail through SMTP (HMS_SMTP_HOST and HMS_SMTP_FROM). A channel with no configuration keeps failing loudly rather than pretending.
     * Both are written from public documentation and unverified against the live services.
     */
    @Bean
    NotificationProvider emailProvider(@Value("${hms.notifications.mode:mock}") String mode,
                                       @Value("${hms.notifications.smtp.host:}") String host, @Value("${hms.notifications.smtp.port:587}") int port,
                                       @Value("${hms.notifications.smtp.username:}") String username, @Value("${hms.notifications.smtp.password:}") String password,
                                       @Value("${hms.notifications.smtp.from:}") String from, @Value("${hms.notifications.smtp.starttls:true}") boolean startTls,
                                       @Value("${hms.notifications.smtp.ssl:false}") boolean ssl) {
        if (!"live".equalsIgnoreCase(mode)) {
            return new MockProvider("EMAIL");
        }
        if (host.isBlank() || from.isBlank()) {
            return new UnconfiguredProvider("EMAIL");
        }
        return new SmtpEmailProvider(new SmtpEmailProvider.Config(host, port, username, password, from, startTls, ssl));
    }

    @Bean
    NotificationProvider smsProvider(@Value("${hms.notifications.mode:mock}") String mode,
                                     @Value("${hms.notifications.sms.at-base-url:https://api.africastalking.com}") String baseUrl,
                                     @Value("${hms.notifications.sms.at-username:}") String username, @Value("${hms.notifications.sms.at-api-key:}") String apiKey,
                                     @Value("${hms.notifications.sms.sender-id:}") String senderId) {
        if (!"live".equalsIgnoreCase(mode)) {
            return new MockProvider("SMS");
        }
        if (username.isBlank() || apiKey.isBlank()) {
            return new UnconfiguredProvider("SMS");
        }
        return new AfricasTalkingSmsProvider(baseUrl, username, apiKey, senderId);
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
