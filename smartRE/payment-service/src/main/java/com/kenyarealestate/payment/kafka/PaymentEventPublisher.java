package com.kenyarealestate.payment.kafka;

import com.kenyarealestate.payment.entity.Payment;
import com.kenyarealestate.payment.entity.PaymentOutboxEvent;
import com.kenyarealestate.payment.service.PaymentOutboxService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Component
public class PaymentEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final PaymentOutboxService outboxService;

    @Value("${kafka.topics.payment-events:payment-events}")
    private String topic;

    public PaymentEventPublisher(KafkaTemplate<String, Object> kafkaTemplate, PaymentOutboxService outboxService) {
        this.kafkaTemplate = kafkaTemplate;
        this.outboxService = outboxService;
    }

    public Events.PaymentCompletedEvent buildPaymentCompletedEvent(Payment payment) {
        return Events.PaymentCompletedEvent.builder()
                .eventType("PAYMENT_COMPLETED")
                .paymentId(payment.getId())
                .buyerId(payment.getBuyerId())
                .sellerId(payment.getSellerId())
                .propertyId(payment.getPropertyId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .mpesaReceiptNumber(payment.getMpesaReceiptNumber())
                .paymentType(payment.getPaymentType().name())
                .billRefNumber(payment.getBillRefNumber())
                .completedAt(LocalDateTime.now())
                .build();
    }

    public UUID recordAndPublish(Payment payment) {
        Events.PaymentCompletedEvent event = buildPaymentCompletedEvent(payment);
        PaymentOutboxEvent outbox = outboxService.recordPending(payment.getId(), "PAYMENT_COMPLETED", topic, event);
        sendToKafka(outbox.getId(), event);
        return outbox.getId();
    }

    public void sendToKafka(UUID outboxEventId, Events.PaymentCompletedEvent event) {
        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, event.getPaymentId().toString(), event);
        String correlationId = MDC.get("traceId");
        if (!StringUtils.hasText(correlationId)) correlationId = UUID.randomUUID().toString();
        record.headers().add("X-Correlation-Id", correlationId.getBytes(StandardCharsets.UTF_8));

        kafkaTemplate.send(record)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish PAYMENT_COMPLETED for paymentId={} (outboxId={}): {}",
                                event.getPaymentId(), outboxEventId, ex.getMessage());
                        outboxService.markAttemptFailed(outboxEventId, ex.getMessage());
                    } else {
                        log.info("Published PAYMENT_COMPLETED paymentId={} receipt={} (outboxId={})",
                                event.getPaymentId(), event.getMpesaReceiptNumber(), outboxEventId);
                        outboxService.markPublished(outboxEventId);
                    }
                });
    }
}
