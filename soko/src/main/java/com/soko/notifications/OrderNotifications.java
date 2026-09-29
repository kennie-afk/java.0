package com.soko.notifications;

import com.soko.domain.AppUser;
import com.soko.domain.Customer;
import com.soko.persistence.CustomerRepository;
import com.soko.persistence.UserRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Turns the events that already matter to a distributor's business (an
 * order placed, a line dispatched, a line delivered) into the emails those
 * events should send, and finds who to send them to.
 *
 * <p>Failures here are swallowed and logged, never thrown: a notification
 * that could not be sent is not a reason to fail the order that triggered
 * it. The order is the real thing that happened; the email is a courtesy
 * about it.
 */
@Service
public class OrderNotifications {

    private static final Logger log = LoggerFactory.getLogger(OrderNotifications.class);

    private final UserRepository users;
    private final EmailNotifier email;
    private final CustomerRepository customers;
    private final SmsSender sms;

    public OrderNotifications(UserRepository users, EmailNotifier email,
            CustomerRepository customers, SmsSender sms) {
        this.users = users;
        this.email = email;
        this.customers = customers;
        this.sms = sms;
    }

    public void orderPlaced(UUID tenantId, UUID customerId, String reference, long totalCents) {
        String amount = ksh(totalCents);
        notifyCustomer(
                tenantId,
                customerId,
                "Order " + reference + " received",
                "Thanks for your order " + reference + ". Total due: " + amount + ".");
        notifyOwners(
                tenantId,
                "New order " + reference,
                "A new order " + reference + " worth " + amount + " was just placed and routed.");
    }

    public void lineDispatched(UUID tenantId, UUID customerId, String reference, String product) {
        notifyCustomer(
                tenantId,
                customerId,
                "On its way: " + reference,
                product + " from order " + reference + " has been dispatched.");
    }

    public void lineDelivered(UUID tenantId, UUID customerId, String reference, String product) {
        notifyCustomer(
                tenantId,
                customerId,
                "Delivered: " + reference,
                product + " from order " + reference + " has been delivered.");
    }

    private void notifyCustomer(UUID tenantId, UUID customerId, String subject, String body) {
        try {
            for (AppUser user : users.findByTenantIdAndCustomerId(tenantId, customerId)) {
                email.send(user.getEmail(), subject, body);
            }
        } catch (RuntimeException e) {
            log.warn("could not notify customer {} of tenant {}: {}", customerId, tenantId, e.getMessage());
        }

        try {
            customers.findByIdAndTenantId(customerId, tenantId)
                    .map(Customer::getPhone)
                    .ifPresent(phone -> sms.send(phone, subject + ": " + body));
        } catch (RuntimeException e) {
            log.warn("could not SMS customer {} of tenant {}: {}", customerId, tenantId, e.getMessage());
        }
    }

    private void notifyOwners(UUID tenantId, String subject, String body) {
        try {
            for (AppUser owner : users.findByTenantIdAndRole(tenantId, "OWNER")) {
                email.send(owner.getEmail(), subject, body);
            }
        } catch (RuntimeException e) {
            log.warn("could not notify the owner(s) of tenant {}: {}", tenantId, e.getMessage());
        }
    }

    private static String ksh(long cents) {
        return "KSh " + String.format("%,.0f", cents / 100.0);
    }
}
