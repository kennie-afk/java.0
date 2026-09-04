package com.kenyarealestate.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kenyarealestate.payment.client.PmsClient;
import com.kenyarealestate.payment.dto.C2bCallbackRequest;
import com.kenyarealestate.payment.dto.RentInvoiceRef;
import com.kenyarealestate.payment.entity.Payment;
import com.kenyarealestate.payment.entity.PaymentType;
import com.kenyarealestate.payment.kafka.PaymentEventPublisher;
import com.kenyarealestate.payment.repository.MpesaRawCallbackRepository;
import com.kenyarealestate.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class C2bServiceTest {

    private static final String REFERENCE = "RNT-202610-B7-ABCD";

    private PaymentRepository payments;
    private MpesaRawCallbackRepository rawCallbacks;
    private PmsClient pmsClient;
    private PaymentEventPublisher publisher;
    private C2bService service;

    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        payments = mock(PaymentRepository.class);
        rawCallbacks = mock(MpesaRawCallbackRepository.class);
        pmsClient = mock(PmsClient.class);
        publisher = mock(PaymentEventPublisher.class);

        when(payments.saveAndFlush(any())).thenAnswer(i -> {
            Payment p = i.getArgument(0, Payment.class);
            if (p.getId() == null) p.setId(UUID.randomUUID());
            return p;
        });
        when(payments.findByMpesaTransactionId(any())).thenReturn(Optional.empty());

        service = new C2bService(payments, rawCallbacks, pmsClient, publisher, mock(PaymentAuditService.class));
    }

    private C2bCallbackRequest request(String ref, String amount, String transId) throws Exception {
        return mapper.readValue("""
                {"TransactionType":"Pay Bill","TransID":"%s","TransTime":"20261005120000",
                 "TransAmount":"%s","BusinessShortCode":"400200","BillRefNumber":"%s",
                 "MSISDN":"254712345678","FirstName":"ACHIENG","LastName":"OTIENO"}
                """.formatted(transId, amount, ref), C2bCallbackRequest.class);
    }

    private RentInvoiceRef invoiceRef(String balance) {
        RentInvoiceRef ref = new RentInvoiceRef();
        ref.setInvoiceId(UUID.randomUUID());
        ref.setLeaseId(UUID.randomUUID());
        ref.setTenantId(UUID.randomUUID());
        ref.setTenantUserId(UUID.randomUUID());
        ref.setLandlordId(UUID.randomUUID());
        ref.setPropertyId(UUID.randomUUID());
        ref.setInvoiceNumber(REFERENCE);
        ref.setBalance(new BigDecimal(balance));
        return ref;
    }

    @Test
    void aKnownAccountNumberWithABalanceIsAccepted() throws Exception {
        when(pmsClient.resolveRentInvoice(REFERENCE)).thenReturn(invoiceRef("18000"));
        assertEquals(C2bService.ACCEPTED,
                service.validate(request(REFERENCE, "18000", "TX1"), "{}").resultCode());
    }

    @Test
    void anUnknownAccountNumberIsRejected() throws Exception {
        when(pmsClient.resolveRentInvoice(any())).thenReturn(null);
        assertEquals(C2bService.REJECTED_INVALID_ACCOUNT,
                service.validate(request("NOPE", "18000", "TX2"), "{}").resultCode());
    }

    @Test
    void anAlreadySettledInvoiceIsRejected() throws Exception {
        when(pmsClient.resolveRentInvoice(REFERENCE)).thenReturn(invoiceRef("0"));
        assertEquals(C2bService.REJECTED_INVALID_ACCOUNT,
                service.validate(request(REFERENCE, "18000", "TX3"), "{}").resultCode());
    }

    @Test
    void aZeroAmountIsRejected() throws Exception {
        when(pmsClient.resolveRentInvoice(REFERENCE)).thenReturn(invoiceRef("18000"));
        assertEquals(C2bService.REJECTED_INVALID_AMOUNT,
                service.validate(request(REFERENCE, "0", "TX4"), "{}").resultCode());
    }

    @Test
    void aConfirmationRecordsARentPaymentAndAnnouncesIt() throws Exception {
        when(pmsClient.resolveRentInvoice(REFERENCE)).thenReturn(invoiceRef("18000"));

        assertEquals(C2bService.ACCEPTED,
                service.confirm(request(REFERENCE, "18000", "TX5"), "{}").resultCode());

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(payments).saveAndFlush(captor.capture());
        Payment p = captor.getValue();
        assertEquals(PaymentType.RENT, p.getPaymentType());
        assertEquals(REFERENCE, p.getBillRefNumber());
        assertEquals("TX5", p.getMpesaTransactionId());
        assertEquals(0, new BigDecimal("18000").compareTo(p.getAmount()));
        verify(publisher).recordAndPublish(any());
    }

    @Test
    void aRepeatedConfirmationIsNotRecordedTwice() throws Exception {
        when(payments.findByMpesaTransactionId("TX6")).thenReturn(Optional.of(Payment.builder().id(UUID.randomUUID()).build()));

        assertEquals(C2bService.ACCEPTED,
                service.confirm(request(REFERENCE, "18000", "TX6"), "{}").resultCode());

        verify(payments, never()).saveAndFlush(any());
        verify(publisher, never()).recordAndPublish(any());
    }

    @Test
    void anUnmatchableConfirmationIsStillAcceptedAndKept() throws Exception {
        when(pmsClient.resolveRentInvoice(any())).thenReturn(null);

        assertEquals(C2bService.ACCEPTED,
                service.confirm(request("MYSTERY", "18000", "TX7"), "{\"raw\":true}").resultCode(),
                "the money has already moved, so refusing it would make Safaricom retry forever");

        verify(payments, never()).saveAndFlush(any());
        verify(rawCallbacks).save(any());
    }

    @Test
    void theRawPayloadIsKeptForEveryConfirmation() throws Exception {
        when(pmsClient.resolveRentInvoice(REFERENCE)).thenReturn(invoiceRef("18000"));
        service.confirm(request(REFERENCE, "18000", "TX8"), "{\"raw\":true}");
        verify(rawCallbacks).save(any());
    }
}
