package com.soko.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.soko.domain.MpesaPayment;
import com.soko.persistence.OrderRepository;
import com.soko.mpesa.StkCallback;
import com.soko.payment.PaymentService;
import com.soko.persistence.InvoiceRepository;
import com.soko.domain.Invoice;
import com.soko.billing.LedgerService;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where Safaricom calls back, not a customer or console. Permitted
 * unauthenticated on purpose (see {@code /v1/public/**} in SecurityConfig) --
 * Daraja cannot present a Soko bearer token. Correlation is entirely by
 * {@code CheckoutRequestID}, which {@link PaymentService} treats as the
 * idempotency key, so a retried callback is safe to receive twice.
 */
@RestController
@RequestMapping("/v1/public/mpesa")
public class MpesaCallbackController {

    private static final Logger log = LoggerFactory.getLogger(MpesaCallbackController.class);

    private final PaymentService payments;
    private final OrderRepository orders;
    private final InvoiceRepository invoices;
    private final LedgerService ledger;
    private final ObjectMapper objectMapper;

    public MpesaCallbackController(PaymentService payments, OrderRepository orders,
            InvoiceRepository invoices, LedgerService ledger, ObjectMapper objectMapper) {
        this.payments = payments;
        this.orders = orders;
        this.invoices = invoices;
        this.ledger = ledger;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/stk-callback")
    public ResponseEntity<Map<String, Object>> callback(
            @RequestBody String rawBody,
            @RequestHeader(name = "X-Mpesa-Signature", required = false) String signature) {
        StkCallback parsed;
        try {
            parsed = objectMapper.readValue(rawBody, StkCallback.class);
        } catch (Exception ex) {
            log.warn("unparseable M-Pesa callback body");
            return ResponseEntity.ok(Map.of("ResultCode", 1, "ResultDesc", "malformed body"));
        }

        PaymentService.CallbackResult result = payments.applyCallback(parsed, rawBody, signature);

        if (result.outcome() == PaymentService.CallbackOutcome.SUCCEEDED) {
            settle(result.payment());
        }

        // Daraja only cares that we returned 200 with ResultCode 0; it is not
        // shown to a human and does not reflect whether the PAYMENT itself
        // succeeded, only that the callback was received and processed.
        return ResponseEntity.ok(Map.of("ResultCode", 0, "ResultDesc", "Accepted"));
    }

    private void settle(MpesaPayment payment) {
        if (payment.getPurpose() == MpesaPayment.Purpose.ORDER) {
            orders.findById(payment.getReferenceId()).ifPresent(order -> {
                order.setStatus("PAID");
                orders.save(order);
                ledger.append(order.getTenantId(), "PAYMENT_RECEIVED", "ORDER", order.getId(),
                        payment.getAmountCents(), "M-Pesa receipt " + payment.getMpesaReceiptNumber());
            });
        } else {
            invoices.findById(payment.getReferenceId()).ifPresent(invoice -> {
                invoice.setStatus(Invoice.Status.PAID);
                invoice.setPaidAt(Instant.now());
                invoices.save(invoice);
                ledger.append(invoice.getTenantId(), "PAYMENT_RECEIVED", "INVOICE", invoice.getId(),
                        payment.getAmountCents(), "M-Pesa receipt " + payment.getMpesaReceiptNumber());
            });
        }
    }
}
