package com.smartseason.order.repo;

import com.smartseason.order.domain.PurchaseOrder;
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
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID>, JpaSpecificationExecutor<PurchaseOrder> {

    Optional<PurchaseOrder> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<PurchaseOrder> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Slice<PurchaseOrder> findAllByTenantIdOrderByCreatedAtDescIdDesc(UUID tenantId, Pageable pageable);

    @Query("SELECT e FROM PurchaseOrder e WHERE e.tenantId = :tenantId "
            + "AND (e.createdAt < :cursorAt "
            + "     OR (e.createdAt = :cursorAt AND e.id < :cursorId)) "
            + "ORDER BY e.createdAt DESC, e.id DESC")
    Slice<PurchaseOrder> findAfterCursor(@Param("tenantId") UUID tenantId,
                                  @Param("cursorAt") Instant cursorAt,
                                  @Param("cursorId") UUID cursorId,
                                  Pageable pageable);

    Optional<PurchaseOrder> findByOrderNumberAndTenantId(String orderNumber, UUID tenantId);
    Page<PurchaseOrder> findAllByBuyerOrgIdAndTenantId(UUID buyerOrgId, UUID tenantId, Pageable pageable);
    Page<PurchaseOrder> findAllBySellerOrgIdAndTenantId(UUID sellerOrgId, UUID tenantId, Pageable pageable);
    Page<PurchaseOrder> findAllByPaymentIntentIdAndTenantId(UUID paymentIntentId, UUID tenantId, Pageable pageable);
    Optional<PurchaseOrder> findByIdempotencyKeyAndTenantId(String idempotencyKey, UUID tenantId);
}
