package com.kenyarealestate.payment.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class PaymentReconciliationJob {

    private final PaymentService paymentService;

    private static final int TIMEOUT_SECONDS = 180;

    /** 25s in production; a demo lowers it so a mock payment settles within seconds. */
    @org.springframework.beans.factory.annotation.Value("${mpesa.reconcile-grace-seconds:25}")
    private int graceSeconds;

    public PaymentReconciliationJob(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @Scheduled(fixedDelayString = "${mpesa.reconcile-interval-ms:15000}", initialDelay = 20_000)
    public void reconcile() {
        var due = paymentService.findStaleStkPushed(graceSeconds);
        var timedOut = paymentService.findStaleStkPushed(TIMEOUT_SECONDS);
        for (UUID id : due) {
            try {
                paymentService.reconcileOne(id, timedOut.contains(id));
            } catch (Exception e) {
                log.error("Payment reconciliation failed for paymentId={}: {}", id, e.getMessage());
            }
        }
    }
}
