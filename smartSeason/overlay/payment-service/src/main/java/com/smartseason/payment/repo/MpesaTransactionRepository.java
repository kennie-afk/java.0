package com.smartseason.payment.repo;

import com.smartseason.payment.domain.MpesaTransaction;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MpesaTransactionRepository extends JpaRepository<MpesaTransaction, UUID> {

    Optional<MpesaTransaction> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<MpesaTransaction> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<MpesaTransaction> findAllByPaymentIntentIdAndTenantId(UUID paymentIntentId, UUID tenantId, Pageable pageable);

    Optional<MpesaTransaction> findByMpesaReceiptNumberAndTenantId(String receipt, UUID tenantId);

    Optional<MpesaTransaction> findByCheckoutRequestId(String checkoutRequestId);

    Optional<MpesaTransaction> findByMpesaReceiptNumber(String mpesaReceiptNumber);
}
