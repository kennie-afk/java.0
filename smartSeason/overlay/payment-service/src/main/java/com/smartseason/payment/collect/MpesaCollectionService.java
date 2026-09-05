package com.smartseason.payment.collect;

import com.smartseason.payment.domain.MpesaTransaction;
import com.smartseason.payment.domain.PaymentIntent;
import com.smartseason.payment.mpesa.MpesaGateway;
import com.smartseason.payment.mpesa.Msisdn;
import com.smartseason.payment.mpesa.StkPushRequest;
import com.smartseason.payment.mpesa.StkPushResponse;
import com.smartseason.payment.platform.DomainRuleException;
import com.smartseason.payment.platform.EventPublisher;
import com.smartseason.payment.platform.TenantContext;
import com.smartseason.payment.repo.MpesaTransactionRepository;
import com.smartseason.payment.repo.PaymentIntentRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MpesaCollectionService {

    private static final Logger log = LoggerFactory.getLogger(MpesaCollectionService.class);

    private final PaymentIntentRepository intents;
    private final MpesaTransactionRepository transactions;
    private final MpesaGateway gateway;
    private final EventPublisher events;
    private final String callbackUrl;

    public MpesaCollectionService(PaymentIntentRepository intents,
                                  MpesaTransactionRepository transactions,
                                  MpesaGateway gateway,
                                  EventPublisher events,
                                  @Value("${smartseason.mpesa.callback-url:http://localhost:8080/api/payment/v1/mpesa/stk-callback}")
                                  String callbackUrl) {
        this.intents = intents;
        this.transactions = transactions;
        this.gateway = gateway;
        this.events = events;
        this.callbackUrl = callbackUrl;
    }

    @Transactional
    public PaymentIntent collect(CollectionRequest request) {
        UUID tenantId = TenantContext.requireTenantId();

        var existing = intents.findByIdempotencyKeyAndTenantId(request.idempotencyKey(), tenantId);
        if (existing.isPresent()) {
            return existing.get();
        }

        String msisdn = Msisdn.normalise(request.payerPhone());

        PaymentIntent intent = new PaymentIntent();
        intent.setTenantId(tenantId);
        intent.setReference("PI-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase());
        intent.setOrderId(request.orderId());
        intent.setPayerOrgId(request.payerOrgId());
        intent.setPayeeOrgId(request.payeeOrgId());
        intent.setAmount(request.amount());
        intent.setCurrency(request.currency());
        intent.setMethod(PaymentIntent.Method.MPESA_STK);
        intent.setPurpose(request.purpose());
        intent.setPayerPhone(msisdn);
        intent.setStatus(PaymentIntent.Status.CREATED);
        intent.setIdempotencyKey(request.idempotencyKey());
        intent.setInitiatedAt(Instant.now());
        intent.setEscrow(request.escrow());
        PaymentIntent saved = intents.save(intent);

        String reference = request.accountReference() == null || request.accountReference().isBlank()
                ? saved.getReference()
                : request.accountReference();

        StkPushResponse response;
        try {
            response = gateway.stkPush(new StkPushRequest(
                    msisdn, request.amount(), reference,
                    "SmartSeason " + request.purpose().name().toLowerCase(), callbackUrl));
        } catch (RuntimeException ex) {
            saved.setStatus(PaymentIntent.Status.FAILED);
            saved.setFailureReason(ex.getMessage());
            intents.save(saved);
            events.publish("money", "PaymentFailed", saved.getId(), saved.getReference());
            throw new DomainRuleException("Could not reach M-Pesa: " + ex.getMessage());
        }

        MpesaTransaction transaction = new MpesaTransaction();
        transaction.setTenantId(tenantId);
        transaction.setPaymentIntentId(saved.getId());
        transaction.setMerchantRequestId(response.merchantRequestId());
        transaction.setCheckoutRequestId(response.checkoutRequestId());
        transaction.setPhoneNumber(msisdn);
        transaction.setAmount(request.amount());
        transaction.setTransactionType(MpesaTransaction.TransactionType.STK_PUSH);
        transaction.setAccountReference(reference);
        transaction.setStatus(response.accepted()
                ? MpesaTransaction.Status.PENDING
                : MpesaTransaction.Status.FAILED);
        transaction.setResultDesc(response.responseDescription());
        transactions.save(transaction);

        if (!response.accepted()) {
            saved.setStatus(PaymentIntent.Status.FAILED);
            saved.setFailureReason(response.responseDescription());
            intents.save(saved);
            events.publish("money", "PaymentFailed", saved.getId(), saved.getReference());
            return saved;
        }

        saved.setStatus(PaymentIntent.Status.PENDING);
        saved.setProviderRef(response.checkoutRequestId());
        intents.save(saved);
        events.publish("money", "PaymentInitiated", saved.getId(), saved.getReference());

        log.info("STK push issued for intent {} ({} {})",
                saved.getReference(), request.currency(), request.amount());
        return saved;
    }
}
