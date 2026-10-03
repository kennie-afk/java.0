package com.hms.notify;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Delivers queued messages. It has no tenant: it claims rows through an owner-rights function that leases them, hands each to the provider
 * for its channel, and records the result. Safe to run on every replica at once.
 */
@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger("hms.notifications");

    private final JdbcClient jdbc;
    private final NotificationService notifications;
    private final Map<String, NotificationProvider> providers;
    private final int batch;
    private final int leaseSeconds;
    private final int maxAttempts;
    private final int backoffSeconds;

    NotificationDispatcher(JdbcClient jdbc, NotificationService notifications, List<NotificationProvider> providers,
                           @Value("${hms.notifications.batch:20}") int batch, @Value("${hms.notifications.lease-seconds:120}") int leaseSeconds,
                           @Value("${hms.notifications.max-attempts:5}") int maxAttempts, @Value("${hms.notifications.backoff-seconds:60}") int backoffSeconds) {
        this.jdbc = jdbc;
        this.notifications = notifications;
        this.providers = providers.stream().collect(Collectors.toMap(NotificationProvider::channel, Function.identity()));
        this.batch = batch;
        this.leaseSeconds = leaseSeconds;
        this.maxAttempts = maxAttempts;
        this.backoffSeconds = backoffSeconds;
    }

    private record Claimed(UUID id, String channel, String recipient, String template, String params) {}

    /** One pass over what is due. Returns how many messages it handled. */
    public int dispatchOnce() {
        List<Claimed> due = jdbc.sql("SELECT id, channel, recipient, template, params::text AS params FROM outbox_claim(?, ?)").params(batch, leaseSeconds)
                .query((rs, n) -> new Claimed(rs.getObject("id", UUID.class), rs.getString("channel"), rs.getString("recipient"), rs.getString("template"), rs.getString("params"))).list();
        for (Claimed c : due) {
            boolean ok = false;
            boolean permanent = false;
            String error = null;
            try {
                NotificationProvider provider = providers.get(c.channel());
                if (provider == null) {
                    throw new NotificationProvider.DeliveryException("No provider for channel " + c.channel(), true);
                }
                JsonNode params = notifications.parse(c.params());
                NotificationTemplates.Rendered r = NotificationTemplates.render(c.template(), params);
                provider.send(new NotificationProvider.Outbound(c.id(), c.channel(), c.recipient(), r.subject(), r.body()));
                ok = true;
            } catch (NotificationProvider.DeliveryException e) {
                permanent = e.permanent();
                error = e.getMessage();
            } catch (RuntimeException e) {
                error = e.getClass().getSimpleName() + ": " + e.getMessage();
            }
            if (!ok) {
                log.warn("notification {} to {} failed: {}", c.id(), NotificationProviders.mask(c.recipient()), error);
            }
            jdbc.sql("SELECT outbox_finish(?, ?, ?, ?, ?, ?)").params(c.id(), ok, permanent, error, maxAttempts, backoffSeconds).query().singleRow();
        }
        return due.size();
    }
}
