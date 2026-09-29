package com.smartseason.payment.repo;

import com.smartseason.payment.domain.MpesaTransaction;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MpesaTransactionRepository extends JpaRepository<MpesaTransaction, UUID> {

    Optional<MpesaTransaction> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<MpesaTransaction> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Slice<MpesaTransaction> findAllByTenantIdOrderByCreatedAtDescIdDesc(UUID tenantId, Pageable pageable);

    @Query("SELECT e FROM MpesaTransaction e WHERE e.tenantId = :tenantId "
            + "AND (e.createdAt < :cursorAt "
            + "     OR (e.createdAt = :cursorAt AND e.id < :cursorId)) "
            + "ORDER BY e.createdAt DESC, e.id DESC")
    Slice<MpesaTransaction> findAfterCursor(@Param("tenantId") UUID tenantId,
                                  @Param("cursorAt") Instant cursorAt,
                                  @Param("cursorId") UUID cursorId,
                                  Pageable pageable);

    Page<MpesaTransaction> findAllByPaymentIntentIdAndTenantId(UUID paymentIntentId, UUID tenantId, Pageable pageable);

    Optional<MpesaTransaction> findByMpesaReceiptNumberAndTenantId(String receipt, UUID tenantId);

    Optional<MpesaTransaction> findByCheckoutRequestId(String checkoutRequestId);

    Optional<MpesaTransaction> findByMpesaReceiptNumber(String mpesaReceiptNumber);
}
