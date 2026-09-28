package com.kenyarealestate.payment.repository;

import com.kenyarealestate.payment.entity.MpesaRawCallback;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MpesaRawCallbackRepository extends JpaRepository<MpesaRawCallback, UUID> {

    List<MpesaRawCallback> findByPaymentIdOrderByReceivedAtAsc(UUID paymentId);

    /**
     * The most recent callbacks, newest first.
     *
     * <p>Replaces {@code findAll()} on this table. Every M-Pesa callback ever received
     * is kept for reconciliation, so that table only grows; returning all of it loaded
     * the entire history into the heap and then serialised it into one response. Whoever
     * is reading this wants the last few, and a bounded page is the only version of this
     * that is still safe in a year.
     */
    List<MpesaRawCallback> findAllByOrderByReceivedAtDesc(Pageable pageable);

    List<MpesaRawCallback> findByCheckoutRequestIdOrderByReceivedAtAsc(String checkoutRequestId);
}
