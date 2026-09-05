package com.kenyarealestate.pms.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kenyarealestate.pms.service.RentPaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PaymentEventConsumerTest {

    private static final String TOPIC = "payment-events";

    @Mock private RentPaymentService rentPayments;

    private PaymentEventConsumer consumer() {
        return new PaymentEventConsumer(new ObjectMapper(), rentPayments);
    }

    private String event(String eventType, String paymentType, String paymentId) {
        return """
                {"eventType":"%s","paymentType":"%s","paymentId":"%s",
                 "amount":45000,"mpesaReceiptNumber":"QK12ABC","billRefNumber":"RNT-202601-FLATB1-ABCD"}
                """.formatted(eventType, paymentType, paymentId);
    }

    @Test
    void settlesRentWhenAPaymentCompletes() {
        UUID paymentId = UUID.randomUUID();

        consumer().consume(event("PAYMENT_COMPLETED", "RENT", paymentId.toString()), TOPIC, null);

        verify(rentPayments).settleFromPayment(eq(paymentId), eq(new BigDecimal("45000")),
                eq("QK12ABC"), eq("RNT-202601-FLATB1-ABCD"));
    }

    @ParameterizedTest
    @CsvSource({
            "PAYMENT_FAILED,RENT",
            "PAYMENT_INITIATED,RENT",
            "PAYMENT_COMPLETED,FULL_PAYMENT",
            "PAYMENT_COMPLETED,VIEWING_FEE",
            "PAYMENT_COMPLETED,DEPOSIT"
    })
    void ignoresEveryEventThatIsNotACompletedRentPayment(String eventType, String paymentType) {
        consumer().consume(event(eventType, paymentType, UUID.randomUUID().toString()), TOPIC, null);

        verifyNoInteractions(rentPayments);
    }

    @Test
    void ignoresARentPaymentWhoseIdIsUnusable() {
        consumer().consume(event("PAYMENT_COMPLETED", "RENT", "not-a-uuid"), TOPIC, null);

        verifyNoInteractions(rentPayments);
    }

    @Test
    void ignoresARentPaymentWithNoIdAtAll() {
        String payload = """
                {"eventType":"PAYMENT_COMPLETED","paymentType":"RENT","amount":45000}
                """;

        consumer().consume(payload, TOPIC, null);

        verifyNoInteractions(rentPayments);
    }

    @Test
    void settlesWithoutAnAmountWhenTheEventOmitsOne() {
        UUID paymentId = UUID.randomUUID();
        String payload = """
                {"eventType":"PAYMENT_COMPLETED","paymentType":"RENT","paymentId":"%s"}
                """.formatted(paymentId);

        consumer().consume(payload, TOPIC, null);

        verify(rentPayments).settleFromPayment(paymentId, null, null, null);
    }

    @Test
    void rejectsAMalformedEventSoTheBrokerCanRetryIt() {
        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> consumer().consume("{not json", TOPIC, null));

        assertEquals("Rent settlement failed for topic " + TOPIC, ex.getMessage());
        verifyNoInteractions(rentPayments);
    }

    @Test
    void propagatesASettlementFailureRatherThanSwallowingIt() {
        doThrow(new IllegalStateException("invoice already settled"))
                .when(rentPayments).settleFromPayment(any(), any(), any(), any());

        assertThrows(RuntimeException.class, () -> consumer()
                .consume(event("PAYMENT_COMPLETED", "RENT", UUID.randomUUID().toString()), TOPIC, null));
    }

    @Test
    void acceptsACorrelationIdHeaderFromTheProducer() {
        UUID paymentId = UUID.randomUUID();
        byte[] correlationId = "trace-abc-123".getBytes(StandardCharsets.UTF_8);

        consumer().consume(event("PAYMENT_COMPLETED", "RENT", paymentId.toString()), TOPIC, correlationId);

        verify(rentPayments).settleFromPayment(eq(paymentId), any(), any(), any());
    }
}
