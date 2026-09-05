package com.smartseason.payment.collect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartseason.payment.domain.MpesaTransaction;
import com.smartseason.payment.domain.PaymentIntent;
import com.smartseason.payment.domain.ProviderCallback;
import com.smartseason.payment.mpesa.MpesaGateway;
import com.smartseason.payment.mpesa.StkCallback;
import com.smartseason.payment.platform.EventPublisher;
import com.smartseason.payment.repo.MpesaTransactionRepository;
import com.smartseason.payment.repo.PaymentIntentRepository;
import com.smartseason.payment.repo.ProviderCallbackRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StkCallbackService {

    private static final Logger log = LoggerFactory.getLogger(StkCallbackService.class);

    private final PaymentIntentRepository intents;
    private final MpesaTransactionRepository transactions;
    private final ProviderCallbackRepository callbacks;
    private final MpesaGateway gateway;
    private final EventPublisher events;
    private final ObjectMapper objectMapper;

    public StkCallbackService(PaymentIntentRepository intents,
                              MpesaTransactionRepository transactions,
                              ProviderCallbackRepository callbacks,
                              MpesaGateway gateway,
                              EventPublisher events,
                              ObjectMapper objectMapper) {
        this.intents = intents;
        this.transactions = transactions;
        this.callbacks = callbacks;
        this.gateway = gateway;
        this.events = events;
        this.objectMapper = objectMapper;
    }

    public enum Outcome { PROCESSED, DUPLICATE, UNKNOWN_TRANSACTION, INVALID_SIGNATURE, MALFORMED }

    @Transactional
    public Outcome handle(String rawBody, String signature) {
        if (!gateway.verifyCallbackSignature(rawBody, signature)) {
            record(null, rawBody, ProviderCallback.Status.INVALID, "signature rejected");
            return Outcome.INVALID_SIGNATURE;
        }

        StkCallback callback;
        try {
            callback = objectMapper.readValue(rawBody, StkCallback.class);
        } catch (Exception ex) {
            record(null, rawBody, ProviderCallback.Status.INVALID, "unparseable body");
            return Outcome.MALFORMED;
        }

        StkCallback.StkCallbackDetail detail = callback.detail();
        if (detail == null || detail.checkoutRequestId() == null) {
            record(null, rawBody, ProviderCallback.Status.INVALID, "no checkout request id");
            return Outcome.MALFORMED;
        }

        var found = transactions.findByCheckoutRequestId(detail.checkoutRequestId());
        if (found.isEmpty()) {
            record(detail.checkoutRequestId(), rawBody, ProviderCallback.Status.FAILED,
                    "no matching transaction");
            log.warn("M-Pesa callback for unknown checkout id {}", detail.checkoutRequestId());
            return Outcome.UNKNOWN_TRANSACTION;
        }

        MpesaTransaction transaction = found.get();

        if (transaction.getStatus() == MpesaTransaction.Status.SUCCESS
                || transaction.getStatus() == MpesaTransaction.Status.FAILED) {
            record(detail.checkoutRequestId(), rawBody, ProviderCallback.Status.DUPLICATE,
                    "transaction already settled");
            log.info("Ignoring duplicate M-Pesa callback for {}", detail.checkoutRequestId());
            return Outcome.DUPLICATE;
        }

        transaction.setResultCode(detail.resultCode());
        transaction.setResultDesc(detail.resultDesc());
        transaction.setRawCallback(rawBody);
        transaction.setTransactionDate(Instant.now());

        PaymentIntent intent = intents.findById(transaction.getPaymentIntentId()).orElse(null);

        if (callback.succeeded()) {
            transaction.setStatus(MpesaTransaction.Status.SUCCESS);
            transaction.setMpesaReceiptNumber(callback.receiptNumber());
            transactions.save(transaction);

            if (intent != null) {
                intent.setStatus(PaymentIntent.Status.SUCCEEDED);
                intent.setCompletedAt(Instant.now());
                intent.setProviderRef(callback.receiptNumber());
                intents.save(intent);
                events.publish("money", "PaymentSucceeded", intent.getId(), intent.getReference());
            }
        } else {
            transaction.setStatus(MpesaTransaction.Status.FAILED);
            transactions.save(transaction);

            if (intent != null) {
                intent.setStatus(PaymentIntent.Status.FAILED);
                intent.setFailureReason(detail.resultDesc());
                intent.setCompletedAt(Instant.now());
                intents.save(intent);
                events.publish("money", "PaymentFailed", intent.getId(), intent.getReference());
            }
        }

        record(detail.checkoutRequestId(), rawBody, ProviderCallback.Status.PROCESSED, null);
        return Outcome.PROCESSED;
    }

    private void record(String externalRef, String rawBody, ProviderCallback.Status status,
                        String error) {
        ProviderCallback entry = new ProviderCallback();
        entry.setTenantId(com.smartseason.payment.platform.TenantContext.tenantId()
                .orElse(new java.util.UUID(0L, 0L)));
        entry.setProvider("MPESA");
        entry.setCallbackType("STK_CALLBACK");
        entry.setExternalRef(externalRef);
        entry.setPayload(rawBody);
        entry.setReceivedAt(Instant.now());
        entry.setProcessedAt(Instant.now());
        entry.setStatus(status);
        entry.setError(error);
        callbacks.save(entry);
    }
}
