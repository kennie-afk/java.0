package com.hms.notify;

import java.util.UUID;

/** One delivery channel (e-mail or SMS). A real implementation sends the message; the mock only records it. */
public interface NotificationProvider {

    /** The channel this provider serves: EMAIL or SMS. */
    String channel();

    /** True when nothing leaves this machine, so the console can say so. */
    boolean isMock();

    /** Delivers one message or throws {@link DeliveryException}. */
    void send(Outbound message) throws DeliveryException;

    record Outbound(UUID id, String channel, String recipient, String subject, String body) {}

    /** {@code permanent} means retrying cannot help (for example the provider is not configured). */
    class DeliveryException extends Exception {
        private final boolean permanent;

        public DeliveryException(String message, boolean permanent) {
            super(message);
            this.permanent = permanent;
        }

        public boolean permanent() {
            return permanent;
        }
    }
}
