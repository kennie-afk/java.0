package com.soko.payment;

import com.soko.domain.MpesaPayment;
import com.soko.mpesa.MpesaException;
import com.soko.mpesa.MpesaGateway;
import com.soko.mpesa.MpesaProperties;
import com.soko.mpesa.StkCallback;
import com.soko.mpesa.StkPushRequest;
import com.soko.mpesa.StkPushResponse;
import com.soko.persistence.MpesaPaymentRepository;
import com.soko.platform.Errors;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Collects money for either an order or a platform invoice through a single
 * STK push pipeline (see {@link MpesaPayment.Purpose}). Callers past this
 * point never touch {@link MpesaGateway} directly.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    /** Result-oblivious to what happens next: the caller decides what SUCCESS means. */
    public enum CallbackOutcome { SUCCEEDED, FAILED, DUPLICATE, UNKNOWN, INVALID }

    private final MpesaPaymentRepository payments;
    private final MpesaGateway gateway;
    private final MpesaProperties properties;

    public PaymentService(MpesaPaymentRepository payments, MpesaGateway gateway,
            MpesaProperties properties) {
        this.payments = payments;
        this.gateway = gateway;
        this.properties = properties;
    }

    @Transactional
    public MpesaPayment initiate(
            UUID tenantId, MpesaPayment.Purpose purpose, UUID referenceId,
            long amountCents, String msisdn, String accountReference, String description) {
        if (amountCents <= 0) {
            throw new Errors.BadRequest("there is nothing due to collect");
        }

        Optional<MpesaPayment> pending =
                payments.findFirstByPurposeAndReferenceIdAndStatusOrderByInitiatedAtDesc(
                        purpose.name(), referenceId, MpesaPayment.Status.PENDING.name());
        if (pending.isPresent()) {
            // A customer who re-taps "pay" before answering the first PIN
            // prompt gets the same push back, not a second one queued behind it.
            return pending.get();
        }

        MpesaPayment record = new MpesaPayment();
        record.setTenantId(tenantId);
        record.setPurpose(purpose);
        record.setReferenceId(referenceId);
        record.setMsisdn(msisdn);
        record.setAmountCents(amountCents);
        record.setStatus(MpesaPayment.Status.PENDING);

        StkPushResponse response;
        try {
            response = gateway.stkPush(new StkPushRequest(
                    msisdn,
                    BigDecimal.valueOf(Math.ceilDiv(amountCents, 100)),
                    accountReference,
                    description,
                    properties.callbackUrl()));
        } catch (MpesaException | IllegalArgumentException ex) {
            record.setStatus(MpesaPayment.Status.FAILED);
            record.setResultDesc(ex.getMessage());
            record.setCompletedAt(Instant.now());
            return payments.save(record);
        }

        record.setMerchantRequestId(response.merchantRequestId());
        record.setCheckoutRequestId(response.checkoutRequestId());
        record.setResultDesc(response.responseDescription());
        if (!response.accepted()) {
            record.setStatus(MpesaPayment.Status.FAILED);
            record.setCompletedAt(Instant.now());
        }
        return payments.save(record);
    }

    public record CallbackResult(CallbackOutcome outcome, MpesaPayment payment) {}

    @Transactional
    public CallbackResult applyCallback(StkCallback callback, String rawBody, String signature) {
        if (!gateway.verifyCallbackSignature(rawBody, signature)) {
            log.warn("M-Pesa callback rejected: bad signature");
            return new CallbackResult(CallbackOutcome.INVALID, null);
        }

        StkCallback.StkCallbackDetail detail = callback.detail();
        if (detail == null || detail.checkoutRequestId() == null) {
            log.warn("M-Pesa callback rejected: no checkout request id");
            return new CallbackResult(CallbackOutcome.INVALID, null);
        }

        MpesaPayment payment = payments.findByCheckoutRequestId(detail.checkoutRequestId())
                .orElse(null);
        if (payment == null) {
            log.warn("M-Pesa callback for unknown checkout id {}", detail.checkoutRequestId());
            return new CallbackResult(CallbackOutcome.UNKNOWN, null);
        }

        if (payment.getStatus() != MpesaPayment.Status.PENDING) {
            // Safaricom retries a callback until it gets a 200; once a
            // transaction has already settled, a replay changes nothing.
            log.info("Ignoring duplicate M-Pesa callback for {}", detail.checkoutRequestId());
            return new CallbackResult(CallbackOutcome.DUPLICATE, payment);
        }

        payment.setResultDesc(detail.resultDesc());
        payment.setCompletedAt(Instant.now());

        if (callback.succeeded()) {
            payment.setStatus(MpesaPayment.Status.SUCCESS);
            payment.setMpesaReceiptNumber(callback.receiptNumber());
            payments.save(payment);
            return new CallbackResult(CallbackOutcome.SUCCEEDED, payment);
        }

        payment.setStatus(MpesaPayment.Status.FAILED);
        payments.save(payment);
        return new CallbackResult(CallbackOutcome.FAILED, payment);
    }

    public List<MpesaPayment> history(MpesaPayment.Purpose purpose, UUID referenceId) {
        return payments.findByPurposeAndReferenceIdOrderByInitiatedAtDesc(
                purpose.name(), referenceId);
    }
}
