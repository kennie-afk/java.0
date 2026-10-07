package com.soko.payment;

import com.soko.billing.LedgerService;
import com.soko.domain.Invoice;
import com.soko.domain.MpesaPayment;
import com.soko.mpesa.StkCallback;
import com.soko.persistence.InvoiceRepository;
import com.soko.persistence.OrderRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies an M-Pesa callback and settles what it paid for in ONE transaction.
 *
 * <p>Previously the payment was marked SUCCESS in one transaction and the order or invoice
 * marked PAID afterwards, outside it. A crash between the two left a payment that was received
 * but never applied, and the retry then reported DUPLICATE and did nothing, so the money sat
 * unmatched for ever. Together with the row lock in
 * {@link com.soko.persistence.MpesaPaymentRepository#findByCheckoutRequestId}, a payment is
 * now applied exactly once: the status change, the PAID mark and the ledger entry commit or
 * roll back as one.
 *
 * <p>The thing paid for is looked up under the payment's own tenant, so a payment can never
 * settle another tenant's order or invoice.
 */
@Service
public class PaymentCallbackHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentCallbackHandler.class);

    private final PaymentService payments;
    private final OrderRepository orders;
    private final InvoiceRepository invoices;
    private final LedgerService ledger;

    public PaymentCallbackHandler(PaymentService payments, OrderRepository orders,
            InvoiceRepository invoices, LedgerService ledger) {
        this.payments = payments;
        this.orders = orders;
        this.invoices = invoices;
        this.ledger = ledger;
    }

    @Transactional
    public PaymentService.CallbackResult handle(StkCallback callback, String rawBody, String secret) {
        PaymentService.CallbackResult result = payments.applyCallback(callback, rawBody, secret);
        if (result.outcome() == PaymentService.CallbackOutcome.SUCCEEDED) {
            settle(result.payment());
        }
        return result;
    }

    private void settle(MpesaPayment payment) {
        if (payment.getPurpose() == MpesaPayment.Purpose.ORDER) {
            orders.findByIdAndTenantId(payment.getReferenceId(), payment.getTenantId()).ifPresentOrElse(order -> {
                if ("CANCELLED".equals(order.getStatus())) {
                    // Stock was restocked and the commission voided at cancellation; marking it
                    // PAID would resurrect it, so the money is held for a refund instead.
                    orphan(payment, "ORDER", order.getId(), "order " + order.getReference()
                            + " was cancelled before the payment arrived");
                    return;
                }
                order.setStatus("PAID");
                orders.save(order);
                ledger.append(order.getTenantId(), "PAYMENT_RECEIVED", "ORDER", order.getId(),
                        payment.getDueCents(), "M-Pesa receipt " + payment.getMpesaReceiptNumber());
                recordRounding(payment, "ORDER", order.getId());
            }, () -> orphan(payment, "ORDER", payment.getReferenceId(),
                    "order " + payment.getReferenceId() + " was not found for this tenant"));
        } else {
            invoices.findByIdAndTenantId(payment.getReferenceId(), payment.getTenantId()).ifPresentOrElse(invoice -> {
                invoice.setStatus(Invoice.Status.PAID);
                invoice.setPaidAt(Instant.now());
                invoices.save(invoice);
                ledger.append(invoice.getTenantId(), "PAYMENT_RECEIVED", "INVOICE", invoice.getId(),
                        payment.getDueCents(), "M-Pesa receipt " + payment.getMpesaReceiptNumber());
                recordRounding(payment, "INVOICE", invoice.getId());
            }, () -> orphan(payment, "INVOICE", payment.getReferenceId(),
                    "invoice " + payment.getReferenceId() + " was not found for this tenant"));
        }
    }

    /**
     * Money Safaricom confirmed that cannot be applied to anything. It must not vanish into a log
     * line: the payment is kept (status ORPHANED, receipt number intact) with the reason, listed
     * for the owner, and a ledger entry records the amount so the books still sum to the statement.
     */
    private void orphan(MpesaPayment payment, String referenceType, java.util.UUID referenceId, String reason) {
        payment.setStatus(MpesaPayment.Status.ORPHANED);
        payment.setOrphanReason(reason.length() > 300 ? reason.substring(0, 300) : reason);
        payments.markOrphaned(payment);
        ledger.append(payment.getTenantId(), "PAYMENT_ORPHANED", referenceType, referenceId,
                payment.getAmountCents(), "Unmatched M-Pesa receipt " + payment.getMpesaReceiptNumber());
        log.error("M-Pesa payment {} (receipt {}) succeeded but cannot be applied: {}",
                payment.getId(), payment.getMpesaReceiptNumber(), reason);
    }

    /** The cents collected beyond what was due, so ledger receipts sum to what Safaricom paid out. */
    private void recordRounding(MpesaPayment payment, String referenceType, java.util.UUID referenceId) {
        long extra = payment.getAmountCents() - payment.getDueCents();
        if (extra > 0) {
            ledger.append(payment.getTenantId(), "ROUNDING_COLLECTED", referenceType, referenceId,
                    extra, "Rounded up to whole shillings for M-Pesa receipt " + payment.getMpesaReceiptNumber());
        }
    }
}
