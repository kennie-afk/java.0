package com.smartseason.payout.repo;

import com.smartseason.payout.domain.PayoutHold;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PayoutHoldRepository extends JpaRepository<PayoutHold, UUID> {

    Optional<PayoutHold> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<PayoutHold> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<PayoutHold> findAllByPayoutItemIdAndTenantId(UUID payoutItemId, UUID tenantId, Pageable pageable);
    Page<PayoutHold> findAllByPayeeIdAndTenantId(UUID payeeId, UUID tenantId, Pageable pageable);
    Page<PayoutHold> findAllByFraudCaseIdAndTenantId(UUID fraudCaseId, UUID tenantId, Pageable pageable);
}
