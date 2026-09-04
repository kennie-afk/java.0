package com.kenyarealestate.payment.service;

import com.kenyarealestate.payment.client.PmsClient;
import com.kenyarealestate.payment.dto.C2bCallbackRequest;
import com.kenyarealestate.payment.dto.RentInvoiceRef;
import com.kenyarealestate.payment.entity.*;
import com.kenyarealestate.payment.kafka.PaymentEventPublisher;
import com.kenyarealestate.payment.repository.MpesaRawCallbackRepository;
import com.kenyarealestate.payment.repository.PaymentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
public class C2bService {

    public static final String ACCEPTED = "0";
    public static final String REJECTED_INVALID_ACCOUNT = "C2B00012";
    public static final String REJECTED_INVALID_AMOUNT = "C2B00013";

    private final PaymentRepository payments;
    private final MpesaRawCallbackRepository rawCallbacks;
    private final PmsClient pmsClient;
    private final PaymentEventPublisher eventPublisher;
    private final PaymentAuditService auditService;

    public C2bService(PaymentRepository payments, MpesaRawCallbackRepository rawCallbacks,
                      PmsClient pmsClient, PaymentEventPublisher eventPublisher,
                      PaymentAuditService auditService) {
        this.payments = payments;
        this.rawCallbacks = rawCallbacks;
        this.pmsClient = pmsClient;
        this.eventPublisher = eventPublisher;
        this.auditService = auditService;
    }

    public record Verdict(String resultCode, String resultDesc) {}

    @Transactional
    public Verdict validate(C2bCallbackRequest req, String rawBody) {
        persistRaw("C2B_VALIDATION", req, rawBody, null);

        String reference = req.reference();
        if (!StringUtils.hasText(reference)) {
            return new Verdict(REJECTED_INVALID_ACCOUNT, "Enter your rent invoice number as the account number.");
        }

        RentInvoiceRef invoice = pmsClient.resolveRentInvoice(reference);
        if (invoice == null || invoice.getInvoiceId() == null) {
            return new Verdict(REJECTED_INVALID_ACCOUNT, "We do not recognise that account number.");
        }
        if (invoice.getBalance() != null && invoice.getBalance().compareTo(BigDecimal.ZERO) <= 0) {
            return new Verdict(REJECTED_INVALID_ACCOUNT, "That invoice is already settled.");
        }

        BigDecimal amount = parseAmount(req.getTransAmount());
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return new Verdict(REJECTED_INVALID_AMOUNT, "Enter an amount greater than zero.");
        }
        return new Verdict(ACCEPTED, "Accepted");
    }

    @Transactional
    public Verdict confirm(C2bCallbackRequest req, String rawBody) {
        String transId = req.getTransId();

        if (StringUtils.hasText(transId)) {
            Optional<Payment> existing = payments.findByMpesaTransactionId(transId);
            if (existing.isPresent()) {
                log.info("C2B confirmation {} already recorded as payment {}", transId, existing.get().getId());
                return new Verdict(ACCEPTED, "Already recorded");
            }
        }

        String reference = req.reference();
        RentInvoiceRef invoice = StringUtils.hasText(reference) ? pmsClient.resolveRentInvoice(reference) : null;
        BigDecimal amount = parseAmount(req.getTransAmount());

        if (invoice == null || invoice.getInvoiceId() == null || amount == null) {
            persistRaw("C2B_CONFIRMATION_UNMATCHED", req, rawBody, null);
            log.error("ALERT: C2B payment {} of {} for account '{}' could not be matched to an invoice. "
                            + "The money has arrived and needs manual allocation.",
                    transId, req.getTransAmount(), reference);
            return new Verdict(ACCEPTED, "Received");
        }

        Payment payment = Payment.builder()
                .buyerId(invoice.getTenantUserId() != null ? invoice.getTenantUserId() : invoice.getTenantId())
                .sellerId(invoice.getLandlordId())
                .propertyId(invoice.getPropertyId())
                .paymentType(PaymentType.RENT)
                .amount(amount)
                .phoneNumber(req.getMsisdn())
                .status(PaymentStatus.COMPLETED)
                .mpesaReceiptNumber(transId)
                .mpesaTransactionId(transId)
                .mpesaTransactionDate(req.getTransTime())
                .billRefNumber(invoice.getInvoiceNumber())
                .idempotencyKey("c2b:" + transId)
                .createdAt(LocalDateTime.now())
                .build();

        try {
            payment = payments.saveAndFlush(payment);
        } catch (DataIntegrityViolationException e) {
            log.info("C2B confirmation {} raced another delivery; treating as already recorded", transId);
            return new Verdict(ACCEPTED, "Already recorded");
        }

        persistRaw("C2B_CONFIRMATION", req, rawBody, payment.getId());
        auditService.log(payment.getId(), null, "C2B_PAYMENT_RECEIVED",
                null, PaymentStatus.COMPLETED.name(),
                null, "MPESA", null, null, amount,
                "Paybill payment " + transId + " for invoice " + invoice.getInvoiceNumber());

        eventPublisher.recordAndPublish(payment);
        log.info("Recorded C2B rent payment {} of {} against invoice {}",
                transId, amount, invoice.getInvoiceNumber());
        return new Verdict(ACCEPTED, "Received");
    }

    private void persistRaw(String type, C2bCallbackRequest req, String rawBody, java.util.UUID paymentId) {
        try {
            rawCallbacks.save(MpesaRawCallback.builder()
                    .callbackType(type)
                    .checkoutRequestId(req.getTransId())
                    .rawPayload(rawBody)
                    .paymentId(paymentId)
                    .build());
        } catch (Exception e) {
            log.warn("Could not persist raw {} callback: {}", type, e.getMessage());
        }
    }

    private BigDecimal parseAmount(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
