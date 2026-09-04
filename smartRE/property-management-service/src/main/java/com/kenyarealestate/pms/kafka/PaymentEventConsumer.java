package com.kenyarealestate.pms.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kenyarealestate.pms.service.RentPaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Slf4j
@Component
public class PaymentEventConsumer {

    private final ObjectMapper mapper;
    private final RentPaymentService rentPayments;

    public PaymentEventConsumer(ObjectMapper mapper, RentPaymentService rentPayments) {
        this.mapper = mapper;
        this.rentPayments = rentPayments;
    }

    @KafkaListener(topics = "payment-events", groupId = "property-management-service")
    public void consume(@Payload String payload,
                        @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                        @Header(name = "X-Correlation-Id", required = false) byte[] correlationIdBytes) {

        String traceId = correlationIdBytes != null
                ? new String(correlationIdBytes, StandardCharsets.UTF_8)
                : UUID.randomUUID().toString();
        org.slf4j.MDC.put("traceId", traceId);

        try {
            JsonNode e = mapper.readTree(payload);
            if (!"PAYMENT_COMPLETED".equals(text(e, "eventType"))) return;
            if (!"RENT".equals(text(e, "paymentType"))) return;

            UUID paymentId = uuid(e, "paymentId");
            if (paymentId == null) {
                log.warn("RENT payment completed with no usable paymentId; ignoring");
                return;
            }
            BigDecimal amount = e.hasNonNull("amount") ? e.get("amount").decimalValue() : null;
            rentPayments.settleFromPayment(paymentId, amount, text(e, "mpesaReceiptNumber"), text(e, "billRefNumber"));
        } catch (Exception ex) {
            log.error("Failed to settle rent from payment event on {}: {}", topic, ex.getMessage(), ex);
            throw new RuntimeException("Rent settlement failed for topic " + topic, ex);
        } finally {
            org.slf4j.MDC.remove("traceId");
        }
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
}
