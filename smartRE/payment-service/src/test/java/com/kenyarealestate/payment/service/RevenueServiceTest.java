package com.kenyarealestate.payment.service;

import com.kenyarealestate.payment.client.MpesaB2cClient;
import com.kenyarealestate.payment.client.PropertyServiceClient;
import com.kenyarealestate.payment.dto.ReleaseEscrowRequest;
import com.kenyarealestate.payment.entity.CompanyRevenue;
import com.kenyarealestate.payment.entity.Payment;
import com.kenyarealestate.payment.entity.PaymentStatus;
import com.kenyarealestate.payment.entity.PaymentType;
import com.kenyarealestate.payment.entity.RevenueStatus;
import com.kenyarealestate.payment.entity.RevenueType;
import com.kenyarealestate.payment.repository.CompanyRevenueRepository;
import com.kenyarealestate.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RevenueServiceTest {

    private static final BigDecimal VIEWING_FEE = BigDecimal.valueOf(200);
    private static final BigDecimal COMMISSION_PCT = BigDecimal.valueOf(2.5);

    @Mock private CompanyRevenueRepository revenueRepo;
    @Mock private PaymentRepository paymentRepo;
    @Mock private MpesaB2cClient b2cClient;
    @Mock private PaymentAuditService auditService;
    @Mock private ReceiptService receiptService;
    @Mock private RevenueAttemptPersister revenueAttemptPersister;
    @Mock private PropertyServiceClient propertyServiceClient;

    @InjectMocks private RevenueService revenueService;

    private UUID paymentId;
    private UUID adminId;
    private UUID propertyId;
    private Payment payment;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(revenueService, "viewingFeeKes", VIEWING_FEE);
        ReflectionTestUtils.setField(revenueService, "commissionPct", COMMISSION_PCT);

        paymentId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        propertyId = UUID.randomUUID();
        payment = Payment.builder()
                .id(paymentId)
                .buyerId(UUID.randomUUID())
                .sellerId(UUID.randomUUID())
                .propertyId(propertyId)
                .paymentType(PaymentType.FULL_PAYMENT)
                .status(PaymentStatus.COMPLETED)
                .amount(BigDecimal.valueOf(15000000))
                .phoneNumber("254708374149")
                .escrowReleased(false)
                .build();
    }

    private ReleaseEscrowRequest b2cRequest() {
        ReleaseEscrowRequest req = new ReleaseEscrowRequest();
        req.setPayoutMethod("MPESA_B2C");
        req.setSellerPhone("254708374149");
        req.setAccountName("Demo Seller");
        req.setNotes("verified deal");
        return req;
    }

    private CompanyRevenue revenueWith(RevenueStatus status) {
        return CompanyRevenue.builder()
                .id(UUID.randomUUID())
                .paymentId(paymentId)
                .revenueType(RevenueType.TRANSACTION_COMMISSION)
                .grossAmount(payment.getAmount())
                .platformFee(new BigDecimal("375000.00"))
                .sellerPayout(new BigDecimal("14625000.00"))
                .status(status)
                .build();
    }

    private RevenueAttemptPersister.PreparedRelease prepared(boolean shortCircuited,
                                                             CompanyRevenue revenue,
                                                             PaymentType type) {
        return new RevenueAttemptPersister.PreparedRelease(
                shortCircuited, revenue,
                payment.getAmount(), new BigDecimal("375000.00"), new BigDecimal("14625000.00"),
                payment.getBuyerId(), payment.getSellerId(), propertyId,
                type, payment.getPhoneNumber());
    }

    private void stubClaim(RevenueAttemptPersister.PreparedRelease result) {
        when(revenueAttemptPersister.lockValidateAndClaim(eq(paymentId), any(), anyString(),
                anyString(), eq(adminId), any(), any())).thenReturn(result);
    }

    @Test
    void recordViewingFee_keepsTheWholeFeeAsPlatformRevenue() {
        when(revenueRepo.existsByPaymentId(paymentId)).thenReturn(false);
        when(revenueRepo.save(any(CompanyRevenue.class))).thenAnswer(inv -> inv.getArgument(0));

        CompanyRevenue rev = revenueService.recordViewingFee(
                paymentId, payment.getBuyerId(), payment.getSellerId(), propertyId);

        assertEquals(RevenueType.VIEWING_FEE, rev.getRevenueType());
        assertEquals(VIEWING_FEE, rev.getGrossAmount());
        assertEquals(VIEWING_FEE, rev.getPlatformFee());
        assertEquals(BigDecimal.ZERO, rev.getSellerPayout());
        assertEquals(RevenueStatus.PAYOUT_COMPLETED, rev.getStatus());
    }

    @Test
    void recordViewingFee_isIdempotentForTheSamePayment() {
        CompanyRevenue existing = revenueWith(RevenueStatus.PAYOUT_COMPLETED);
        when(revenueRepo.existsByPaymentId(paymentId)).thenReturn(true);
        when(revenueRepo.findByPaymentId(paymentId)).thenReturn(Optional.of(existing));

        CompanyRevenue rev = revenueService.recordViewingFee(
                paymentId, payment.getBuyerId(), payment.getSellerId(), propertyId);

        assertEquals(existing.getId(), rev.getId());
        verify(revenueRepo, never()).save(any(CompanyRevenue.class));
    }

    @Test
    void releaseEscrow_paysOutAndCompletesTheSaleOnTheHappyPath() {
        CompanyRevenue claimed = revenueWith(RevenueStatus.PAYOUT_INITIATING);
        stubClaim(prepared(false, claimed, PaymentType.FULL_PAYMENT));
        when(b2cClient.payToPhone(anyString(), anyString(), anyString(), any()))
                .thenReturn(new MpesaB2cClient.B2cResult(true, "AG-CONV-1", "AG-ORIG-1", "Accepted"));
        when(revenueAttemptPersister.markInitiated(claimed.getId(), "AG-CONV-1", "AG-ORIG-1"))
                .thenReturn(revenueWith(RevenueStatus.PAYOUT_INITIATED));
        when(paymentRepo.findById(paymentId)).thenReturn(Optional.of(payment));

        assertNotNull(revenueService.releaseEscrowWithPayout(paymentId, adminId, "10.0.0.1", b2cRequest()));

        verify(b2cClient).payToPhone(eq("254708374149"), eq("14625000.00"), anyString(), eq(claimed.getId()));
        verify(revenueAttemptPersister).finalizeEscrowRelease(paymentId, adminId);
        verify(receiptService).issueReceipt(eq(payment), eq(new BigDecimal("375000.00")),
                eq(new BigDecimal("14625000.00")), eq("254708374149"), eq("MPESA_B2C"), eq("Demo Seller"));
        verify(propertyServiceClient).markTransactionComplete(propertyId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"VIEWING_FEE", "PROFILE_ACCESS", "RENT", "COMMISSION"})
    void releaseEscrow_doesNotCompleteTheSaleForNonPurchasePayments(String paymentType) {
        CompanyRevenue claimed = revenueWith(RevenueStatus.PAYOUT_INITIATING);
        stubClaim(prepared(false, claimed, PaymentType.valueOf(paymentType)));
        when(b2cClient.payToPhone(anyString(), anyString(), anyString(), any()))
                .thenReturn(new MpesaB2cClient.B2cResult(true, "AG-CONV-1", "AG-ORIG-1", "Accepted"));
        when(revenueAttemptPersister.markInitiated(any(), anyString(), anyString()))
                .thenReturn(revenueWith(RevenueStatus.PAYOUT_INITIATED));
        when(paymentRepo.findById(paymentId)).thenReturn(Optional.of(payment));

        revenueService.releaseEscrowWithPayout(paymentId, adminId, "10.0.0.1", b2cRequest());

        verifyNoInteractions(propertyServiceClient);
    }

    @Test
    void releaseEscrow_makesNoSecondTransferWhenAPayoutIsAlreadyInFlight() {
        stubClaim(prepared(true, revenueWith(RevenueStatus.PAYOUT_INITIATED), PaymentType.FULL_PAYMENT));

        revenueService.releaseEscrowWithPayout(paymentId, adminId, "10.0.0.1", b2cRequest());

        verifyNoInteractions(b2cClient);
        verify(revenueAttemptPersister, never()).markInitiated(any(), anyString(), anyString());
        verify(revenueAttemptPersister, never()).finalizeEscrowRelease(any(), any());
        verifyNoInteractions(receiptService);
    }

    @Test
    void releaseEscrow_reconcilesTheEscrowFlagWhenAnEarlierPayoutCompletedButNeverFinalised() {
        stubClaim(prepared(true, revenueWith(RevenueStatus.PAYOUT_COMPLETED), PaymentType.FULL_PAYMENT));

        revenueService.releaseEscrowWithPayout(paymentId, adminId, "10.0.0.1", b2cRequest());

        verifyNoInteractions(b2cClient);
        verify(revenueAttemptPersister).finalizeEscrowRelease(paymentId, adminId);
    }

    @Test
    void releaseEscrow_leavesEscrowHeldWhenTheTransferIsRejected() {
        CompanyRevenue claimed = revenueWith(RevenueStatus.PAYOUT_INITIATING);
        stubClaim(prepared(false, claimed, PaymentType.FULL_PAYMENT));
        when(b2cClient.payToPhone(anyString(), anyString(), anyString(), any()))
                .thenReturn(new MpesaB2cClient.B2cResult(false, null, null, "Insufficient float"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> revenueService.releaseEscrowWithPayout(paymentId, adminId, "10.0.0.1", b2cRequest()));

        assertTrue(ex.getMessage().contains("Insufficient float"));
        verify(revenueAttemptPersister).markFailed(claimed.getId(), "Insufficient float");
        verify(revenueAttemptPersister, never()).finalizeEscrowRelease(any(), any());
        verifyNoInteractions(receiptService);
        verifyNoInteractions(propertyServiceClient);
    }

    @Test
    void releaseEscrow_treatsBankTransferAsManualAndMovesNoMoney() {
        CompanyRevenue claimed = revenueWith(RevenueStatus.PAYOUT_INITIATING);
        stubClaim(prepared(false, claimed, PaymentType.FULL_PAYMENT));

        ReleaseEscrowRequest req = new ReleaseEscrowRequest();
        req.setPayoutMethod("BANK_TRANSFER");
        req.setBankAccountNumber("0123456789");

        assertThrows(RuntimeException.class,
                () -> revenueService.releaseEscrowWithPayout(paymentId, adminId, "10.0.0.1", req));

        verifyNoInteractions(b2cClient);
        verify(revenueAttemptPersister).markFailed(claimed.getId(), "BANK_TRANSFER_MANUAL");
    }

    @Test
    void rejectAndRefund_refundsTheBuyerAndVoidsTheReceipt() {
        when(paymentRepo.findById(paymentId)).thenReturn(Optional.of(payment));
        when(b2cClient.payToPhone(anyString(), anyString(), anyString(), any()))
                .thenReturn(new MpesaB2cClient.B2cResult(true, "AG-CONV-2", "AG-ORIG-2", "Accepted"));

        revenueService.rejectAndRefund(paymentId, adminId, "10.0.0.1", "title defect");

        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
        verify(paymentRepo).save(payment);
        verify(receiptService).voidReceiptIfExists(eq(paymentId), anyString());
    }

    @Test
    void rejectAndRefund_refusesOnceEscrowHasBeenReleasedToTheSeller() {
        payment.setEscrowReleased(true);
        when(paymentRepo.findById(paymentId)).thenReturn(Optional.of(payment));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> revenueService.rejectAndRefund(paymentId, adminId, "10.0.0.1", "too late"));

        assertTrue(ex.getMessage().contains("already been released"));
        verifyNoInteractions(b2cClient);
    }

    @Test
    void rejectAndRefund_refusesUnlessThePaymentIsCompleted() {
        payment.setStatus(PaymentStatus.PENDING);
        when(paymentRepo.findById(paymentId)).thenReturn(Optional.of(payment));

        assertThrows(RuntimeException.class,
                () -> revenueService.rejectAndRefund(paymentId, adminId, "10.0.0.1", "not paid"));

        verifyNoInteractions(b2cClient);
    }

    @Test
    void rejectAndRefund_leavesThePaymentCompletedWhenTheRefundIsRejected() {
        when(paymentRepo.findById(paymentId)).thenReturn(Optional.of(payment));
        when(b2cClient.payToPhone(anyString(), anyString(), anyString(), any()))
                .thenReturn(new MpesaB2cClient.B2cResult(false, null, null, "Invalid MSISDN"));

        assertThrows(RuntimeException.class,
                () -> revenueService.rejectAndRefund(paymentId, adminId, "10.0.0.1", "title defect"));

        assertEquals(PaymentStatus.COMPLETED, payment.getStatus());
        verify(paymentRepo, never()).save(any(Payment.class));
        verifyNoInteractions(receiptService);
    }

    @Test
    void refundViewingFee_reversesTheRevenueRowAsWellAsThePayment() {
        payment.setPaymentType(PaymentType.VIEWING_FEE);
        payment.setAmount(VIEWING_FEE);
        CompanyRevenue revenue = revenueWith(RevenueStatus.PAYOUT_COMPLETED);
        when(paymentRepo.findById(paymentId)).thenReturn(Optional.of(payment));
        when(b2cClient.payToPhone(anyString(), anyString(), anyString(), any()))
                .thenReturn(new MpesaB2cClient.B2cResult(true, "AG-CONV-3", "AG-ORIG-3", "Accepted"));
        when(revenueRepo.findByPaymentId(paymentId)).thenReturn(Optional.of(revenue));

        revenueService.refundViewingFee(paymentId, "viewing cancelled");

        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
        verify(revenueRepo).delete(revenue);
        verify(receiptService).voidReceiptIfExists(eq(paymentId), anyString());
    }

    @Test
    void refundViewingFee_refusesAPaymentThatIsNotAViewingFee() {
        when(paymentRepo.findById(paymentId)).thenReturn(Optional.of(payment));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> revenueService.refundViewingFee(paymentId, "wrong type"));

        assertTrue(ex.getMessage().contains("Not a viewing fee payment"));
        verifyNoInteractions(b2cClient);
    }

    @Test
    void handleB2cCallback_completesThePayoutAndStoresTheSafaricomReceipt() {
        CompanyRevenue revenue = revenueWith(RevenueStatus.PAYOUT_INITIATED);
        when(revenueRepo.findByB2cOriginatorConversationId("AG-ORIG-1")).thenReturn(Optional.of(revenue));

        revenueService.handleB2cCallback("AG-ORIG-1", "{}", true, "QK12ABC", null, revenue.getId());

        assertEquals(RevenueStatus.PAYOUT_COMPLETED, revenue.getStatus());
        assertEquals("QK12ABC", revenue.getSellerPayoutReceipt());
        assertNotNull(revenue.getSellerPayoutAt());
        verify(revenueRepo).save(revenue);
    }

    @Test
    void handleB2cCallback_marksThePayoutFailedWithSafaricomsReason() {
        CompanyRevenue revenue = revenueWith(RevenueStatus.PAYOUT_INITIATED);
        when(revenueRepo.findByB2cOriginatorConversationId("AG-ORIG-1")).thenReturn(Optional.of(revenue));

        revenueService.handleB2cCallback("AG-ORIG-1", "{}", false, null, "Invalid MSISDN", revenue.getId());

        assertEquals(RevenueStatus.PAYOUT_FAILED, revenue.getStatus());
        assertEquals("Invalid MSISDN", revenue.getPayoutFailureReason());
    }

    @Test
    void handleStatusQueryCallback_completesOnlyOnAConfirmedCompletion() {
        CompanyRevenue revenue = revenueWith(RevenueStatus.PAYOUT_INITIATED);
        when(revenueRepo.findByStatusQueryConversationId("Q-1")).thenReturn(Optional.of(revenue));

        revenueService.handleStatusQueryCallback("Q-1", "Completed", "{}");

        assertEquals(RevenueStatus.PAYOUT_COMPLETED, revenue.getStatus());
        assertNotNull(revenue.getSellerPayoutAt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"Pending", "Failed", "Unknown"})
    void handleStatusQueryCallback_neverMarksRealMoneyFailedOnAnInconclusiveAnswer(String status) {
        CompanyRevenue revenue = revenueWith(RevenueStatus.PAYOUT_INITIATED);
        when(revenueRepo.findByStatusQueryConversationId("Q-1")).thenReturn(Optional.of(revenue));

        revenueService.handleStatusQueryCallback("Q-1", status, "{}");

        assertEquals(RevenueStatus.PAYOUT_INITIATED, revenue.getStatus());
        verify(revenueRepo, never()).save(any(CompanyRevenue.class));

        ArgumentCaptor<String> event = ArgumentCaptor.forClass(String.class);
        verify(auditService).log(any(), any(), event.capture(), anyString(), anyString(),
                any(), anyString(), any(), any(), any(), anyString());
        assertEquals("STATUS_QUERY_INCONCLUSIVE", event.getValue());
    }

    @Test
    void handleStatusQueryCallback_ignoresAPayoutThatIsNoLongerInFlight() {
        CompanyRevenue revenue = revenueWith(RevenueStatus.PAYOUT_COMPLETED);
        when(revenueRepo.findByStatusQueryConversationId("Q-1")).thenReturn(Optional.of(revenue));

        revenueService.handleStatusQueryCallback("Q-1", "Completed", "{}");

        verify(revenueRepo, never()).save(any(CompanyRevenue.class));
        verifyNoInteractions(auditService);
    }
}
