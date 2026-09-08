package com.kenyarealestate.notification.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kenyarealestate.notification.entity.Category;
import com.kenyarealestate.notification.service.DispatchCommand;
import com.kenyarealestate.notification.service.NotificationDispatcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class EventRouter {

    private final ObjectMapper mapper;
    private final NotificationDispatcher dispatcher;
    private final String frontendUrl;

    public EventRouter(ObjectMapper mapper,
                       NotificationDispatcher dispatcher,
                       @Value("${notification.frontend-url}") String frontendUrl) {
        this.mapper = mapper;
        this.dispatcher = dispatcher;
        this.frontendUrl = frontendUrl;
    }

    public void route(String rawPayload, String topic) throws Exception {
        JsonNode e = mapper.readTree(rawPayload);
        String eventType = text(e, "eventType");
        if (eventType == null) {
            log.warn("Event on topic {} has no eventType field; ignoring", topic);
            return;
        }

        switch (eventType) {
            case "IDENTITY_APPROVED"  -> identityApproved(e);
            case "OWNERSHIP_APPROVED" -> ownershipApproved(e);
            case "PAYMENT_COMPLETED"  -> paymentCompleted(e);
            case "RENT_INVOICE_ISSUED" -> rentInvoiceIssued(e);
            case "RENT_OVERDUE"        -> rentOverdue(e);
            case "RENT_RECEIVED"       -> rentReceived(e);
            case "MAINTENANCE_RAISED"   -> maintenanceRaised(e);
            case "MAINTENANCE_RESOLVED" -> maintenanceResolved(e);
            case "LEASE_ACTIVATED"      -> leaseActivated(e);
            case "LEASE_ENDED"          -> leaseEnded(e);
            case "VIEWING_COMPLETED"    -> viewingCompleted(e);
            case "LISTINGS_SUSPENDED"   -> listingsSuspended(e);
            default -> log.debug("No notification mapped for eventType={} on topic={}", eventType, topic);
        }
    }

    private void identityApproved(JsonNode e) {
        UUID sellerId = uuid(e, "sellerId");
        if (sellerId == null) return;

        Map<String, Object> model = new HashMap<>();
        model.put("expiresAt", text(e, "expiresAt"));
        model.put("dashboardLink", frontendUrl + "/verification");

        dispatcher.dispatch(DispatchCommand.builder()
                .userId(sellerId)
                .templateCode("IDENTITY_APPROVED")
                .category(Category.VERIFICATION)
                .sourceEventType("IDENTITY_APPROVED")
                .sourceEventId(text(e, "verificationId"))
                .entityType("VERIFICATION")
                .entityId(uuid(e, "verificationId"))
                .actionUrl("/verification")
                .model(model)
                .build());
    }

    private void ownershipApproved(JsonNode e) {
        UUID sellerId = uuid(e, "sellerId");
        if (sellerId == null) return;

        String propertyId = text(e, "propertyId");
        Map<String, Object> model = new HashMap<>();
        model.put("parcelNumber", orDash(text(e, "parcelNumber")));
        model.put("titleDeedNumber", orDash(text(e, "titleDeedNumber")));
        model.put("propertyLink", propertyId == null ? frontendUrl + "/listings"
                : frontendUrl + "/properties/" + propertyId);

        dispatcher.dispatch(DispatchCommand.builder()
                .userId(sellerId)
                .templateCode("OWNERSHIP_APPROVED")
                .category(Category.VERIFICATION)
                .sourceEventType("OWNERSHIP_APPROVED")
                .sourceEventId(text(e, "verificationId"))
                .entityType("PROPERTY")
                .entityId(uuid(e, "propertyId"))
                .actionUrl(propertyId == null ? "/listings" : "/properties/" + propertyId)
                .model(model)
                .build());
    }

    private void paymentCompleted(JsonNode e) {
        UUID buyerId = uuid(e, "buyerId");
        if (buyerId == null) return;

        String paymentId = text(e, "paymentId");
        Map<String, Object> model = new HashMap<>();
        model.put("amount", money(text(e, "amount")));
        model.put("currency", e.hasNonNull("currency") ? e.get("currency").asText() : "KES");
        model.put("mpesaReceiptNumber", orDash(text(e, "mpesaReceiptNumber")));
        model.put("paymentType", orDash(text(e, "paymentType")));
        model.put("receiptLink", paymentId == null ? frontendUrl + "/payments"
                : frontendUrl + "/payments/" + paymentId);

        dispatcher.dispatch(DispatchCommand.builder()
                .userId(buyerId)
                .templateCode("PAYMENT_COMPLETED")
                .category(Category.PAYMENT)
                .sourceEventType("PAYMENT_COMPLETED")
                .sourceEventId(paymentId)
                .entityType("PAYMENT")
                .entityId(uuid(e, "paymentId"))
                .actionUrl(paymentId == null ? "/payments" : "/payments/" + paymentId)
                .model(model)
                .build());

        notifySellerOfPayment(e, paymentId);
    }

    /**
     * The other side of a payment.
     *
     * <p>Only the buyer used to be told, which meant a seller could have their property
     * paid for and learn about it by refreshing a page. The event has always carried
     * sellerId; nothing was reading it.
     *
     * <p>Not every payment is a sale. A VIEWING_FEE or PROFILE_ACCESS payment is money to
     * the seller but does not move the property, so it gets the neutral "payment
     * received" wording; a DEPOSIT or FULL_PAYMENT is the thing they have been waiting
     * for and says so. Telling a seller their house has sold when a stranger paid to view
     * it would be a serious message to get wrong.
     */
    private void notifySellerOfPayment(JsonNode e, String paymentId) {
        UUID sellerId = uuid(e, "sellerId");
        if (sellerId == null) return;

        // Rent is settled between landlord and tenant through RENT_RECEIVED, which is
        // already delivered with the right wording and the right link. Sending this as
        // well would be the same news twice.
        String paymentType = text(e, "paymentType");
        if ("RENT".equals(paymentType)) return;

        boolean isSale = "DEPOSIT".equals(paymentType) || "FULL_PAYMENT".equals(paymentType);

        Map<String, Object> model = new HashMap<>();
        model.put("amount", money(text(e, "amount")));
        model.put("currency", e.hasNonNull("currency") ? e.get("currency").asText() : "KES");
        model.put("mpesaReceiptNumber", orDash(text(e, "mpesaReceiptNumber")));
        model.put("paymentType", orDash(paymentType));
        model.put("receiptLink", paymentId == null ? frontendUrl + "/payments"
                : frontendUrl + "/payments/" + paymentId);

        dispatcher.dispatch(DispatchCommand.builder()
                .userId(sellerId)
                .templateCode(isSale ? "SALE_PAYMENT_RECEIVED" : "SELLER_PAYMENT_RECEIVED")
                .category(Category.PAYMENT)
                .sourceEventType("PAYMENT_COMPLETED")
                // Distinct from the buyer's key: the dedup key is per user and channel,
                // so both sides can be told about the same payment exactly once each.
                .sourceEventId(paymentId)
                .entityType("PAYMENT")
                .entityId(uuid(e, "paymentId"))
                .actionUrl(paymentId == null ? "/payments" : "/payments/" + paymentId)
                .model(model)
                .build());
    }

    private void rentInvoiceIssued(JsonNode e) {
        UUID recipient = uuid(e, "tenantUserId");
        if (recipient == null) return;

        Map<String, Object> model = new HashMap<>();
        model.put("unitLabel", orDash(text(e, "unitLabel")));
        model.put("invoiceNumber", orDash(text(e, "invoiceNumber")));
        model.put("amountDue", money(text(e, "amountDue")));
        model.put("dueDate", orDash(text(e, "dueDate")));
        model.put("invoiceLink", frontendUrl + "/my-tenancy");

        dispatcher.dispatch(DispatchCommand.builder()
                .userId(recipient)
                .templateCode("RENT_DUE")
                .category(Category.TENANCY)
                .sourceEventType("RENT_INVOICE_ISSUED")
                .sourceEventId(text(e, "invoiceId"))
                .entityType("INVOICE")
                .entityId(uuid(e, "invoiceId"))
                .actionUrl("/my-tenancy")
                .model(model)
                .build());
    }

    private void rentOverdue(JsonNode e) {
        UUID recipient = uuid(e, "tenantUserId");
        if (recipient == null) return;

        String days = e.hasNonNull("daysOverdue") ? e.get("daysOverdue").asText() : "0";
        Map<String, Object> model = new HashMap<>();
        model.put("unitLabel", orDash(text(e, "unitLabel")));
        model.put("invoiceNumber", orDash(text(e, "invoiceNumber")));
        model.put("balance", money(text(e, "balance")));
        model.put("dueDate", orDash(text(e, "dueDate")));
        model.put("daysOverdue", days);
        model.put("invoiceLink", frontendUrl + "/my-tenancy");

        dispatcher.dispatch(DispatchCommand.builder()
                .userId(recipient)
                .templateCode("RENT_OVERDUE")
                .category(Category.TENANCY)
                .sourceEventType("RENT_OVERDUE")
                .sourceEventId(text(e, "invoiceId") + ":" + days)
                .entityType("INVOICE")
                .entityId(uuid(e, "invoiceId"))
                .actionUrl("/my-tenancy")
                .model(model)
                .build());
    }

    private void rentReceived(JsonNode e) {
        UUID recipient = uuid(e, "tenantUserId");
        if (recipient == null) return;

        Map<String, Object> model = new HashMap<>();
        model.put("unitLabel", orDash(text(e, "unitLabel")));
        model.put("invoiceNumber", orDash(text(e, "invoiceNumber")));
        model.put("amount", money(text(e, "amount")));
        model.put("balance", money(text(e, "balance")));
        model.put("invoiceLink", frontendUrl + "/my-tenancy");

        dispatcher.dispatch(DispatchCommand.builder()
                .userId(recipient)
                .templateCode("RENT_RECEIVED")
                .category(Category.TENANCY)
                .sourceEventType("RENT_RECEIVED")
                .sourceEventId(text(e, "invoiceId") + ":" + orDash(text(e, "amount")))
                .entityType("INVOICE")
                .entityId(uuid(e, "invoiceId"))
                .actionUrl("/my-tenancy")
                .model(model)
                .build());
    }

    private void maintenanceRaised(JsonNode e) {
        UUID landlordId = uuid(e, "landlordId");
        if (landlordId == null) return;
        if ("LANDLORD".equals(text(e, "raisedByRole"))) return;

        Map<String, Object> model = new HashMap<>();
        model.put("unitLabel", orDash(text(e, "unitLabel")));
        model.put("reference", orDash(text(e, "reference")));
        model.put("category", orDash(text(e, "category")));
        model.put("priority", orDash(text(e, "priority")));
        model.put("title", orDash(text(e, "title")));
        model.put("requestLink", frontendUrl + "/portfolio?tab=maintenance");

        dispatcher.dispatch(DispatchCommand.builder()
                .userId(landlordId)
                .templateCode("MAINTENANCE_RAISED")
                .category(Category.MAINTENANCE)
                .sourceEventType("MAINTENANCE_RAISED")
                .sourceEventId(text(e, "requestId"))
                .entityType("MAINTENANCE")
                .entityId(uuid(e, "requestId"))
                .actionUrl("/portfolio?tab=maintenance")
                .model(model)
                .build());
    }

    private void maintenanceResolved(JsonNode e) {
        UUID recipient = uuid(e, "tenantUserId");
        if (recipient == null) return;

        String status = orDash(text(e, "status")).toLowerCase(java.util.Locale.ROOT);
        Map<String, Object> model = new HashMap<>();
        model.put("unitLabel", orDash(text(e, "unitLabel")));
        model.put("reference", orDash(text(e, "reference")));
        model.put("title", orDash(text(e, "title")));
        model.put("status", status);
        model.put("resolutionNotes", orDash(text(e, "resolutionNotes")));
        model.put("requestLink", frontendUrl + "/my-tenancy");

        dispatcher.dispatch(DispatchCommand.builder()
                .userId(recipient)
                .templateCode("MAINTENANCE_RESOLVED")
                .category(Category.MAINTENANCE)
                .sourceEventType("MAINTENANCE_RESOLVED")
                .sourceEventId(text(e, "requestId") + ":" + status)
                .entityType("MAINTENANCE")
                .entityId(uuid(e, "requestId"))
                .actionUrl("/my-tenancy")
                .model(model)
                .build());
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private static UUID uuid(JsonNode node, String field) {
        String v = text(node, field);
        if (v == null) return null;
        try {
            return UUID.fromString(v);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String orDash(String v) { return v == null ? "—" : v; }

    /**
     * Money, as a person expects to read it.
     *
     * <p>The raw value arrives from JSON as whatever the producing service's BigDecimal
     * serialised to — "25000.0", "15000", "4500000.00" — and putting that straight into
     * a template produced lines like "KES 25000.0" in real emails. Grouping separators
     * and exactly two decimal places, or none at all when the amount is whole, because
     * "KES 25,000" reads as money and "KES 25000.0" reads as a database field.
     *
     * <p>Anything unparseable is passed through untouched rather than replaced with a
     * dash: a number we cannot format is still better information than no number.
     */
    private static String money(String raw) {
        if (raw == null) return "—";
        try {
            java.math.BigDecimal v = new java.math.BigDecimal(raw.trim());
            boolean whole = v.stripTrailingZeros().scale() <= 0;
            java.text.DecimalFormat f = new java.text.DecimalFormat(whole ? "#,##0" : "#,##0.00");
            return f.format(v);
        } catch (NumberFormatException e) {
            return raw;
        }
    }

    /**
     * A tenancy going live is the moment rent starts and the dashboard becomes the
     * tenant's, so it is worth an email rather than only a row appearing somewhere.
     *
     * <p>Addressed to tenantUserId, not tenantId: the latter is a row in the landlord's
     * book and may belong to nobody with an account. When it is absent there is simply
     * no one to write to, which is a normal state and not a failure.
     */
    private void leaseActivated(JsonNode e) {
        UUID tenantUserId = uuid(e, "tenantUserId");
        if (tenantUserId == null) return;

        Map<String, Object> model = new HashMap<>();
        model.put("unitLabel", orDash(text(e, "unitLabel")));
        model.put("rentAmount", money(text(e, "rentAmount")));
        model.put("billingDay", orDash(text(e, "billingDay")));
        model.put("startDate", orDash(text(e, "startDate")));
        model.put("tenancyLink", frontendUrl + "/my-tenancy");

        dispatcher.dispatch(DispatchCommand.builder()
                .userId(tenantUserId)
                .templateCode("LEASE_ACTIVATED")
                .category(Category.TENANCY)
                .sourceEventType("LEASE_ACTIVATED")
                .sourceEventId(text(e, "leaseId"))
                .entityType("LEASE")
                .entityId(uuid(e, "leaseId"))
                .actionUrl("/my-tenancy")
                .model(model)
                .build());
    }

    /** The end of a tenancy has deposit and notice consequences; silence is not kind. */
    private void leaseEnded(JsonNode e) {
        UUID tenantUserId = uuid(e, "tenantUserId");
        if (tenantUserId == null) return;

        Map<String, Object> model = new HashMap<>();
        model.put("unitLabel", orDash(text(e, "unitLabel")));
        model.put("reason", orDash(text(e, "reason")));
        model.put("tenancyLink", frontendUrl + "/my-tenancy");

        dispatcher.dispatch(DispatchCommand.builder()
                .userId(tenantUserId)
                .templateCode("LEASE_ENDED")
                .category(Category.TENANCY)
                .sourceEventType("LEASE_ENDED")
                .sourceEventId(text(e, "leaseId"))
                .entityType("LEASE")
                .entityId(uuid(e, "leaseId"))
                .actionUrl("/my-tenancy")
                .model(model)
                .build());
    }

    /**
     * Completing a viewing is what earns a buyer the right to review, and a right nobody
     * mentions is one nobody exercises. This is the event that makes the review system
     * work at all.
     */
    private void viewingCompleted(JsonNode e) {
        UUID buyerId = uuid(e, "buyerId");
        if (buyerId == null) return;

        String propertyId = text(e, "propertyId");
        Map<String, Object> model = new HashMap<>();
        model.put("reviewLink", propertyId == null ? frontendUrl + "/reviews"
                : frontendUrl + "/properties/" + propertyId + "#review");

        dispatcher.dispatch(DispatchCommand.builder()
                .userId(buyerId)
                .templateCode("VIEWING_COMPLETED")
                .category(Category.VIEWING)
                .sourceEventType("VIEWING_COMPLETED")
                .sourceEventId(text(e, "viewingId"))
                .entityType("VIEWING")
                .entityId(uuid(e, "viewingId"))
                .actionUrl(propertyId == null ? "/reviews" : "/properties/" + propertyId)
                .model(model)
                .build());
    }

    /**
     * A seller's listings have been pulled from the marketplace.
     *
     * <p>Reaches the seller on every channel they allow, SMS included — this is the case
     * the SMS budget exists for. Their property has stopped earning and the reason is
     * something only we know.
     */
    private void listingsSuspended(JsonNode e) {
        UUID sellerId = uuid(e, "sellerId");
        if (sellerId == null) return;

        Map<String, Object> model = new HashMap<>();
        model.put("suspendedCount", orDash(text(e, "suspendedCount")));
        model.put("reason", orDash(text(e, "reason")));
        model.put("listingsLink", frontendUrl + "/manage-listings");

        dispatcher.dispatch(DispatchCommand.builder()
                .userId(sellerId)
                .templateCode("LISTINGS_SUSPENDED")
                .category(Category.PROPERTY)
                .sourceEventType("LISTINGS_SUSPENDED")
                // Keyed on the suspension moment rather than the seller, so a later
                // suspension for a different reason is a new message rather than a
                // duplicate that gets swallowed.
                .sourceEventId(sellerId + ":" + text(e, "suspendedAt"))
                .entityType("PROPERTY")
                .actionUrl("/manage-listings")
                .model(model)
                .build());
    }
}
