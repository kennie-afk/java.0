package com.smartseason.payment.repo;

import com.smartseason.payment.domain.EscrowHold;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EscrowHoldRepository extends JpaRepository<EscrowHold, UUID> {

    Optional<EscrowHold> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<EscrowHold> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<EscrowHold> findAllByPaymentIntentIdAndTenantId(UUID paymentIntentId, UUID tenantId, Pageable pageable);
    Page<EscrowHold> findAllByOrderIdAndTenantId(UUID orderId, UUID tenantId, Pageable pageable);
}
