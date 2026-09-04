package com.kenyarealestate.pms.service;

import com.kenyarealestate.pms.client.PaymentServiceClient;
import com.kenyarealestate.pms.dto.PayInvoiceRequest;
import com.kenyarealestate.pms.dto.RecordPaymentRequest;
import com.kenyarealestate.pms.entity.*;
import com.kenyarealestate.pms.exception.ConflictException;
import com.kenyarealestate.pms.exception.ForbiddenException;
import com.kenyarealestate.pms.kafka.PmsEventPublisher;
import com.kenyarealestate.pms.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RentPaymentServiceTest {

    private static final UUID LANDLORD  = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID TENANT_USER = UUID.randomUUID();
    private static final UUID INVOICE_ID = UUID.randomUUID();
    private static final UUID PAYMENT_ID = UUID.randomUUID();

    private RentInvoiceRepository invoices;
    private RentPaymentRepository payments;
    private TenantRepository tenants;
    private UnitRepository units;
    private PaymentServiceClient paymentClient;
    private RentInvoiceService invoiceService;
    private PmsEventPublisher publisher;
    private RentPaymentService service;

    private RentInvoice invoice;

    @BeforeEach
    void setUp() {
        invoices = mock(RentInvoiceRepository.class);
        payments = mock(RentPaymentRepository.class);
        tenants = mock(TenantRepository.class);
        units = mock(UnitRepository.class);
        paymentClient = mock(PaymentServiceClient.class);
        invoiceService = mock(RentInvoiceService.class);
        publisher = mock(PmsEventPublisher.class);

        invoice = RentInvoice.builder()
                .id(INVOICE_ID).leaseId(UUID.randomUUID()).unitId(UUID.randomUUID())
                .tenantId(TENANT_ID).landlordId(LANDLORD).invoiceNumber("RNT-202610-B7-ABCD")
                .periodStart(LocalDate.of(2026, 10, 5)).periodEnd(LocalDate.of(2026, 11, 4))
                .dueDate(LocalDate.of(2026, 10, 10))
                .amountDue(new BigDecimal("18000")).amountPaid(BigDecimal.ZERO)
                .status(InvoiceStatus.PENDING).build();

        when(invoices.findById(INVOICE_ID)).thenReturn(Optional.of(invoice));
        when(invoices.save(any())).thenAnswer(i -> i.getArgument(0));
        when(payments.save(any())).thenAnswer(i -> i.getArgument(0, RentPayment.class));
        when(payments.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0, RentPayment.class));
        when(tenants.findById(TENANT_ID)).thenReturn(Optional.of(
                Tenant.builder().id(TENANT_ID).landlordId(LANDLORD).userId(TENANT_USER)
                        .fullName("Achieng Otieno").phone("254712345678").build()));
        when(invoiceService.requireOwned(LANDLORD, INVOICE_ID)).thenReturn(invoice);

        service = new RentPaymentService(invoices, payments, mock(LeaseRepository.class), tenants, units,
                paymentClient, invoiceService, publisher);
    }

    private RentPayment pending(BigDecimal amount) {
        return RentPayment.builder().id(UUID.randomUUID()).invoiceId(INVOICE_ID)
                .leaseId(invoice.getLeaseId()).paymentId(PAYMENT_ID).amount(amount)
                .method(PaymentMethod.MPESA_STK).status(RentPaymentStatus.PENDING).build();
    }

    @Test
    void aLandlordCannotStartAnMpesaPromptOnTheTenantsBehalf() {
        ForbiddenException e = assertThrows(ForbiddenException.class,
                () -> service.startStkPush(LANDLORD, "tok", INVOICE_ID, PayInvoiceRequest.builder().build()));
        assertTrue(e.getMessage().contains("record the payment instead"));
    }

    @Test
    void payingAnAlreadySettledInvoiceIsRefused() {
        invoice.setStatus(InvoiceStatus.PAID);
        assertThrows(ConflictException.class,
                () -> service.startStkPush(TENANT_USER, "tok", INVOICE_ID, PayInvoiceRequest.builder().build()));
    }

    @Test
    void overpayingIsRefused() {
        assertThrows(ConflictException.class, () -> service.startStkPush(TENANT_USER, "tok", INVOICE_ID,
                PayInvoiceRequest.builder().amount(new BigDecimal("20000")).build()));
    }

    @Test
    void aConfirmedPaymentInFullSettlesTheInvoice() {
        when(payments.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(pending(new BigDecimal("18000"))));

        service.settleFromPayment(PAYMENT_ID, new BigDecimal("18000"), "QCB7Y2XK91", null);

        assertEquals(InvoiceStatus.PAID, invoice.getStatus());
        assertEquals(0, new BigDecimal("18000").compareTo(invoice.getAmountPaid()));
        assertEquals(0, BigDecimal.ZERO.compareTo(invoice.getBalance()));
        verify(publisher).publishRentReceived(any(), any(), any());
    }

    @Test
    void aPartialPaymentLeavesTheInvoicePartial() {
        when(payments.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(pending(new BigDecimal("8000"))));

        service.settleFromPayment(PAYMENT_ID, new BigDecimal("8000"), "QCB7Y2XK92", null);

        assertEquals(InvoiceStatus.PARTIAL, invoice.getStatus());
        assertEquals(0, new BigDecimal("10000").compareTo(invoice.getBalance()));
    }

    @Test
    void aRedeliveredPaymentEventDoesNotCreditTwice() {
        RentPayment already = pending(new BigDecimal("18000"));
        already.setStatus(RentPaymentStatus.CONFIRMED);
        when(payments.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(already));

        service.settleFromPayment(PAYMENT_ID, new BigDecimal("18000"), "QCB7Y2XK91", null);

        assertEquals(0, BigDecimal.ZERO.compareTo(invoice.getAmountPaid()),
                "an at-least-once redelivery must not double-credit the invoice");
        verify(publisher, never()).publishRentReceived(any(), any(), any());
    }

    @Test
    void aPaymentWeDidNotStartIsIgnored() {
        when(payments.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.empty());
        service.settleFromPayment(PAYMENT_ID, new BigDecimal("500"), "QCB7Y2XK93", null);
        verify(invoices, never()).save(any());
    }

    @Test
    void aLandlordCanRecordCashAgainstAnInvoice() {
        var res = service.recordManual(LANDLORD, INVOICE_ID, RecordPaymentRequest.builder()
                .amount(new BigDecimal("18000")).method("CASH").note("Paid at the office").build());

        assertEquals("CONFIRMED", res.getStatus());
        assertEquals(InvoiceStatus.PAID, invoice.getStatus());
    }

    @Test
    void anStkPushCannotBeRecordedByHand() {
        ConflictException e = assertThrows(ConflictException.class,
                () -> service.recordManual(LANDLORD, INVOICE_ID, RecordPaymentRequest.builder()
                        .amount(new BigDecimal("1000")).method("MPESA_STK").build()));
        assertTrue(e.getMessage().contains("started by the tenant"));
    }

    @Test
    void recordingMoreThanIsOwedIsRefused() {
        assertThrows(ConflictException.class, () -> service.recordManual(LANDLORD, INVOICE_ID,
                RecordPaymentRequest.builder().amount(new BigDecimal("25000")).method("CASH").build()));
    }

    @Test
    void anUnpromptedPaybillPaymentIsMatchedByItsAccountNumber() {
        when(payments.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.empty());
        when(invoices.findByInvoiceNumber("RNT-202610-B7-ABCD")).thenReturn(Optional.of(invoice));

        service.settleFromPayment(PAYMENT_ID, new BigDecimal("18000"), "QCB7Y2XK95", "RNT-202610-B7-ABCD");

        assertEquals(InvoiceStatus.PAID, invoice.getStatus());
        assertEquals(0, new BigDecimal("18000").compareTo(invoice.getAmountPaid()));
    }

    @Test
    void aPaybillAccountNumberIsMatchedRegardlessOfCase() {
        when(payments.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.empty());
        when(invoices.findByInvoiceNumber("RNT-202610-B7-ABCD")).thenReturn(Optional.of(invoice));

        service.settleFromPayment(PAYMENT_ID, new BigDecimal("18000"), "QCB7Y2XK96", " rnt-202610-b7-abcd ");

        assertEquals(InvoiceStatus.PAID, invoice.getStatus());
    }

    @Test
    void aPaybillOverpaymentCreditsOnlyTheOutstandingBalance() {
        when(payments.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.empty());
        when(invoices.findByInvoiceNumber("RNT-202610-B7-ABCD")).thenReturn(Optional.of(invoice));

        service.settleFromPayment(PAYMENT_ID, new BigDecimal("25000"), "QCB7Y2XK97", "RNT-202610-B7-ABCD");

        assertEquals(0, new BigDecimal("18000").compareTo(invoice.getAmountPaid()),
                "the excess must not inflate the invoice; it is left for manual handling");
        assertEquals(InvoiceStatus.PAID, invoice.getStatus());
    }

    @Test
    void aPaybillPaymentQuotingAnUnknownAccountIsNotSilentlyLost() {
        when(payments.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.empty());
        when(invoices.findByInvoiceNumber("RNT-NOPE")).thenReturn(Optional.empty());

        service.settleFromPayment(PAYMENT_ID, new BigDecimal("5000"), "QCB7Y2XK98", "RNT-NOPE");

        verify(invoices, never()).save(any());
        verify(publisher, never()).publishRentReceived(any(), any(), any());
    }

    @Test
    void aPaybillPaymentAgainstAnAlreadySettledInvoiceIsNotCredited() {
        invoice.setAmountPaid(new BigDecimal("18000"));
        invoice.setStatus(InvoiceStatus.PAID);
        when(payments.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.empty());
        when(invoices.findByInvoiceNumber("RNT-202610-B7-ABCD")).thenReturn(Optional.of(invoice));

        service.settleFromPayment(PAYMENT_ID, new BigDecimal("18000"), "QCB7Y2XK99", "RNT-202610-B7-ABCD");

        assertEquals(0, new BigDecimal("18000").compareTo(invoice.getAmountPaid()));
        verify(publisher, never()).publishRentReceived(any(), any(), any());
    }

    @Test
    void aPaymentThatArrivesAfterTheDueDateLeavesTheInvoiceOverdueNotPartial() {
        invoice.setDueDate(LocalDate.now().minusDays(3));
        when(payments.findByPaymentId(PAYMENT_ID)).thenReturn(Optional.of(pending(new BigDecimal("5000"))));

        service.settleFromPayment(PAYMENT_ID, new BigDecimal("5000"), "QCB7Y2XK94", null);

        assertEquals(InvoiceStatus.OVERDUE, invoice.getStatus());
    }
}
