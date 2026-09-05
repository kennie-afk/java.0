package com.kenyarealestate.payment.service;

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
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RevenueAttemptPersisterTest {

    private static final BigDecimal COMMISSION_PCT = BigDecimal.valueOf(2.5);

    @Mock private CompanyRevenueRepository revenueRepo;
    @Mock private PaymentRepository paymentRepo;

    @InjectMocks private RevenueAttemptPersister persister;

    private UUID paymentId;
    private UUID adminId;
    private Payment payment;

    @BeforeEach
    void setup() {
        paymentId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        payment = Payment.builder()
                .id(paymentId)
                .buyerId(UUID.randomUUID())
                .sellerId(UUID.randomUUID())
                .propertyId(UUID.randomUUID())
                .paymentType(PaymentType.FULL_PAYMENT)
                .status(PaymentStatus.COMPLETED)
                .amount(BigDecimal.valueOf(15000000))
                .phoneNumber("254708374149")
                .escrowReleased(false)
                .build();
    }

    private CompanyRevenue revenueWith(RevenueStatus status) {
        return CompanyRevenue.builder()
                .id(UUID.randomUUID())
                .paymentId(paymentId)
                .revenueType(RevenueType.TRANSACTION_COMMISSION)
                .grossAmount(payment.getAmount())
                .status(status)
                .build();
    }

    private RevenueAttemptPersister.PreparedRelease claim() {
        return persister.lockValidateAndClaim(paymentId, COMMISSION_PCT,
                "MPESA_B2C", "254708374149", adminId, "notes", "Demo Seller");
    }

    @Test
    void lockValidateAndClaim_splitsGrossIntoCommissionAndPayout() {
        when(paymentRepo.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));
        when(revenueRepo.findByPaymentId(paymentId)).thenReturn(Optional.empty());
        when(revenueRepo.save(any(CompanyRevenue.class))).thenAnswer(inv -> inv.getArgument(0));

        RevenueAttemptPersister.PreparedRelease prepared = claim();

        assertEquals(0, new BigDecimal("15000000").compareTo(prepared.gross()));
        assertEquals(new BigDecimal("375000.00"), prepared.fee());
        assertEquals(new BigDecimal("14625000.00"), prepared.payout());
        assertEquals(0, prepared.gross().compareTo(prepared.fee().add(prepared.payout())));
    }

    @Test
    void lockValidateAndClaim_roundsCommissionToTwoDecimalPlacesHalfUp() {
        payment.setAmount(new BigDecimal("1001"));
        when(paymentRepo.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));
        when(revenueRepo.findByPaymentId(paymentId)).thenReturn(Optional.empty());
        when(revenueRepo.save(any(CompanyRevenue.class))).thenAnswer(inv -> inv.getArgument(0));

        RevenueAttemptPersister.PreparedRelease prepared = claim();

        assertEquals(new BigDecimal("25.03"), prepared.fee());
        assertEquals(new BigDecimal("975.97"), prepared.payout());
        assertEquals(0, prepared.gross().compareTo(prepared.fee().add(prepared.payout())));
    }

    @Test
    void lockValidateAndClaim_createsRevenueInInitiatingState() {
        when(paymentRepo.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));
        when(revenueRepo.findByPaymentId(paymentId)).thenReturn(Optional.empty());
        when(revenueRepo.save(any(CompanyRevenue.class))).thenAnswer(inv -> inv.getArgument(0));

        RevenueAttemptPersister.PreparedRelease prepared = claim();

        assertFalse(prepared.shortCircuited());
        CompanyRevenue saved = prepared.revenue();
        assertEquals(RevenueStatus.PAYOUT_INITIATING, saved.getStatus());
        assertEquals(RevenueType.TRANSACTION_COMMISSION, saved.getRevenueType());
        assertEquals(paymentId, saved.getPaymentId());
        assertEquals(adminId, saved.getReleasedByAdminId());
        assertEquals("MPESA_B2C", saved.getPayoutMethod());
        assertEquals("254708374149", saved.getPayoutIdentifier());
        assertEquals(PaymentType.FULL_PAYMENT, prepared.paymentType());
    }

    @ParameterizedTest
    @EnumSource(value = RevenueStatus.class,
            names = {"PAYOUT_INITIATING", "PAYOUT_INITIATED", "PAYOUT_COMPLETED"})
    void lockValidateAndClaim_shortCircuitsWhenPayoutAlreadyInFlightOrDone(RevenueStatus status) {
        CompanyRevenue existing = revenueWith(status);
        when(paymentRepo.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));
        when(revenueRepo.findByPaymentId(paymentId)).thenReturn(Optional.of(existing));

        RevenueAttemptPersister.PreparedRelease prepared = claim();

        assertTrue(prepared.shortCircuited());
        assertEquals(existing.getId(), prepared.revenue().getId());
        verify(revenueRepo, never()).save(any(CompanyRevenue.class));
    }

    @ParameterizedTest
    @EnumSource(value = RevenueStatus.class, names = {"PENDING", "PAYOUT_FAILED"})
    void lockValidateAndClaim_reclaimsAfterFailedOrPendingAttempt(RevenueStatus status) {
        CompanyRevenue existing = revenueWith(status);
        existing.setPayoutFailureReason("insufficient float");
        when(paymentRepo.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));
        when(revenueRepo.findByPaymentId(paymentId)).thenReturn(Optional.of(existing));
        when(revenueRepo.save(any(CompanyRevenue.class))).thenAnswer(inv -> inv.getArgument(0));

        RevenueAttemptPersister.PreparedRelease prepared = claim();

        assertFalse(prepared.shortCircuited());
        assertEquals(RevenueStatus.PAYOUT_INITIATING, prepared.revenue().getStatus());
        assertNull(prepared.revenue().getPayoutFailureReason());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = "COMPLETED", mode = EnumSource.Mode.EXCLUDE)
    void lockValidateAndClaim_rejectsPaymentThatIsNotCompleted(PaymentStatus status) {
        payment.setStatus(status);
        when(paymentRepo.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));

        RuntimeException ex = assertThrows(RuntimeException.class, this::claim);

        assertTrue(ex.getMessage().contains(status.name()));
        verify(revenueRepo, never()).save(any(CompanyRevenue.class));
    }

    @Test
    void lockValidateAndClaim_rejectsPaymentWhoseEscrowIsAlreadyReleased() {
        payment.setEscrowReleased(true);
        when(paymentRepo.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));

        RuntimeException ex = assertThrows(RuntimeException.class, this::claim);

        assertTrue(ex.getMessage().contains("already released"));
        verify(revenueRepo, never()).save(any(CompanyRevenue.class));
    }

    @Test
    void lockValidateAndClaim_rejectsUnknownPayment() {
        when(paymentRepo.findByIdForUpdate(paymentId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, this::claim);
    }

    @Test
    void markFailed_recordsReasonAgainstTheRevenueRow() {
        CompanyRevenue revenue = revenueWith(RevenueStatus.PAYOUT_INITIATING);
        when(revenueRepo.findById(revenue.getId())).thenReturn(Optional.of(revenue));

        persister.markFailed(revenue.getId(), "insufficient float");

        assertEquals(RevenueStatus.PAYOUT_FAILED, revenue.getStatus());
        assertEquals("insufficient float", revenue.getPayoutFailureReason());
        verify(revenueRepo).save(revenue);
    }

    @Test
    void markInitiated_storesBothSafaricomCorrelationIds() {
        CompanyRevenue revenue = revenueWith(RevenueStatus.PAYOUT_INITIATING);
        when(revenueRepo.findById(revenue.getId())).thenReturn(Optional.of(revenue));
        when(revenueRepo.save(revenue)).thenReturn(revenue);

        persister.markInitiated(revenue.getId(), "AG-CONV-1", "AG-ORIG-1");

        assertEquals(RevenueStatus.PAYOUT_INITIATED, revenue.getStatus());
        assertEquals("AG-CONV-1", revenue.getB2cConversationId());
        assertEquals("AG-ORIG-1", revenue.getB2cOriginatorConversationId());
    }

    @Test
    void finalizeEscrowRelease_flipsFlagAndStampsAdmin() {
        when(paymentRepo.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));

        persister.finalizeEscrowRelease(paymentId, adminId);

        assertTrue(payment.isEscrowReleased());
        assertEquals(adminId, payment.getEscrowReleasedBy());
        verify(paymentRepo).save(payment);
    }

    @Test
    void finalizeEscrowRelease_isIdempotentOnAnAlreadyReleasedPayment() {
        UUID firstAdmin = UUID.randomUUID();
        payment.setEscrowReleased(true);
        payment.setEscrowReleasedBy(firstAdmin);
        when(paymentRepo.findByIdForUpdate(paymentId)).thenReturn(Optional.of(payment));

        persister.finalizeEscrowRelease(paymentId, adminId);

        assertEquals(firstAdmin, payment.getEscrowReleasedBy());
        verify(paymentRepo, never()).save(any(Payment.class));
    }
}
