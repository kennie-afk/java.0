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
        model.put("amount", orDash(text(e, "amount")));
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
    }

    private void rentInvoiceIssued(JsonNode e) {
        UUID recipient = uuid(e, "tenantUserId");
        if (recipient == null) return;

        Map<String, Object> model = new HashMap<>();
        model.put("unitLabel", orDash(text(e, "unitLabel")));
        model.put("invoiceNumber", orDash(text(e, "invoiceNumber")));
        model.put("amountDue", orDash(text(e, "amountDue")));
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
        model.put("balance", orDash(text(e, "balance")));
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
        model.put("amount", orDash(text(e, "amount")));
        model.put("balance", orDash(text(e, "balance")));
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
}
