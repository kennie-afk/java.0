package com.soko.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.soko.billing.LedgerService;
import com.soko.domain.MpesaPayment;
import com.soko.domain.SalesOrder;
import com.soko.persistence.InvoiceRepository;
import com.soko.persistence.OrderRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** What happens to money Safaricom confirmed when the thing it paid for cannot be settled. */
class PaymentCallbackHandlerTest {

    private final PaymentService payments = mock(PaymentService.class);
    private final OrderRepository orders = mock(OrderRepository.class);
    private final InvoiceRepository invoices = mock(InvoiceRepository.class);
    private final LedgerService ledger = mock(LedgerService.class);
    private final PaymentCallbackHandler handler =
            new PaymentCallbackHandler(payments, orders, invoices, ledger);

    private final UUID tenant = UUID.randomUUID();
    private final UUID reference = UUID.randomUUID();
    private MpesaPayment payment;

    @BeforeEach
    void succeededPayment() {
        payment = new MpesaPayment();
        payment.setId(UUID.randomUUID());
        payment.setTenantId(tenant);
        payment.setReferenceId(reference);
        payment.setDueCents(25_000);
        payment.setAmountCents(25_000);
        payment.setMpesaReceiptNumber("RCP123");
        payment.setStatus(MpesaPayment.Status.SUCCESS);
        when(payments.applyCallback(any(), any(), any())).thenReturn(
                new PaymentService.CallbackResult(PaymentService.CallbackOutcome.SUCCEEDED, payment));
    }

    @Test
    void aSucceededPaymentForAMissingOrderIsOrphanedNotLogged() {
        payment.setPurpose(MpesaPayment.Purpose.ORDER);
        when(orders.findByIdAndTenantId(reference, tenant)).thenReturn(Optional.empty());

        handler.handle(null, "{}", "s");

        assertThat(payment.getStatus()).isEqualTo(MpesaPayment.Status.ORPHANED);
        assertThat(payment.getOrphanReason()).contains("was not found");
        // The receipt number is kept: it is what a refund will be matched against.
        assertThat(payment.getMpesaReceiptNumber()).isEqualTo("RCP123");
        verify(payments).markOrphaned(payment);
        verify(ledger).append(eq(tenant), eq("PAYMENT_ORPHANED"), eq("ORDER"), eq(reference),
                eq(25_000L), any());
    }

    @Test
    void aSucceededPaymentForAMissingInvoiceIsOrphanedToo() {
        payment.setPurpose(MpesaPayment.Purpose.INVOICE);
        when(invoices.findByIdAndTenantId(reference, tenant)).thenReturn(Optional.empty());

        handler.handle(null, "{}", "s");

        assertThat(payment.getStatus()).isEqualTo(MpesaPayment.Status.ORPHANED);
        assertThat(payment.getOrphanReason()).contains("invoice");
        verify(ledger).append(eq(tenant), eq("PAYMENT_ORPHANED"), eq("INVOICE"), eq(reference),
                eq(25_000L), any());
    }

    @Test
    void paymentForAnOrderCancelledInTheMeantimeIsHeldNotUsedToResurrectIt() {
        payment.setPurpose(MpesaPayment.Purpose.ORDER);
        SalesOrder order = new SalesOrder();
        order.setId(reference);
        order.setTenantId(tenant);
        order.setReference("SO-1");
        order.setStatus("CANCELLED");
        when(orders.findByIdAndTenantId(reference, tenant)).thenReturn(Optional.of(order));

        handler.handle(null, "{}", "s");

        assertThat(order.getStatus()).isEqualTo("CANCELLED");
        assertThat(payment.getStatus()).isEqualTo(MpesaPayment.Status.ORPHANED);
        assertThat(payment.getOrphanReason()).contains("cancelled");
        verify(orders, never()).save(any());
    }

    @Test
    void aMatchedOrderIsStillMarkedPaidWithNoOrphan() {
        payment.setPurpose(MpesaPayment.Purpose.ORDER);
        SalesOrder order = new SalesOrder();
        order.setId(reference);
        order.setTenantId(tenant);
        order.setStatus("ROUTED");
        when(orders.findByIdAndTenantId(reference, tenant)).thenReturn(Optional.of(order));

        handler.handle(null, "{}", "s");

        assertThat(order.getStatus()).isEqualTo("PAID");
        assertThat(payment.getStatus()).isEqualTo(MpesaPayment.Status.SUCCESS);
        verify(payments, never()).markOrphaned(any());
        verify(ledger).append(eq(tenant), eq("PAYMENT_RECEIVED"), eq("ORDER"), eq(reference),
                eq(25_000L), any());
    }

}
