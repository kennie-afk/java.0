package com.smartseason.payment.repo;

import com.smartseason.payment.domain.PaymentIntent;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentIntentRepository extends JpaRepository<PaymentIntent, UUID>, JpaSpecificationExecutor<PaymentIntent> {

    Optional<PaymentIntent> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<PaymentIntent> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Slice<PaymentIntent> findAllByTenantIdOrderByCreatedAtDescIdDesc(UUID tenantId, Pageable pageable);

    @Query("SELECT e FROM PaymentIntent e WHERE e.tenantId = :tenantId "
            + "AND (e.createdAt < :cursorAt "
            + "     OR (e.createdAt = :cursorAt AND e.id < :cursorId)) "
            + "ORDER BY e.createdAt DESC, e.id DESC")
    Slice<PaymentIntent> findAfterCursor(@Param("tenantId") UUID tenantId,
                                  @Param("cursorAt") Instant cursorAt,
                                  @Param("cursorId") UUID cursorId,
                                  Pageable pageable);

    Optional<PaymentIntent> findByReferenceAndTenantId(String reference, UUID tenantId);

    Optional<PaymentIntent> findByIdempotencyKeyAndTenantId(String idempotencyKey, UUID tenantId);

    Page<PaymentIntent> findAllByOrderIdAndTenantId(UUID orderId, UUID tenantId, Pageable pageable);

    Page<PaymentIntent> findAllByPayerOrgIdAndTenantId(UUID payerOrgId, UUID tenantId, Pageable pageable);

    Page<PaymentIntent> findAllByPayeeOrgIdAndTenantId(UUID payeeOrgId, UUID tenantId, Pageable pageable);

    Page<PaymentIntent> findAllByProviderRefAndTenantId(String providerRef, UUID tenantId, Pageable pageable);
}
