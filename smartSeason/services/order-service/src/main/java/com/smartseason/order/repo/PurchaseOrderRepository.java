package com.smartseason.order.repo;

import com.smartseason.order.domain.PurchaseOrder;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    Optional<PurchaseOrder> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<PurchaseOrder> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Optional<PurchaseOrder> findByOrderNumberAndTenantId(String orderNumber, UUID tenantId);
    Page<PurchaseOrder> findAllByBuyerOrgIdAndTenantId(UUID buyerOrgId, UUID tenantId, Pageable pageable);
    Page<PurchaseOrder> findAllBySellerOrgIdAndTenantId(UUID sellerOrgId, UUID tenantId, Pageable pageable);
    Page<PurchaseOrder> findAllByPaymentIntentIdAndTenantId(UUID paymentIntentId, UUID tenantId, Pageable pageable);
    Optional<PurchaseOrder> findByIdempotencyKeyAndTenantId(String idempotencyKey, UUID tenantId);
}
