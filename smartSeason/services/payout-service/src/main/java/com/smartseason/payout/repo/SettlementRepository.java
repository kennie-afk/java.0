package com.smartseason.payout.repo;

import com.smartseason.payout.domain.Settlement;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SettlementRepository extends JpaRepository<Settlement, UUID> {

    Optional<Settlement> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<Settlement> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Settlement> findBySettlementNumberAndTenantId(String settlementNumber, UUID tenantId);
    Page<Settlement> findAllByPayeeOrgIdAndTenantId(UUID payeeOrgId, UUID tenantId, Pageable pageable);
    Page<Settlement> findAllByPayeeUserIdAndTenantId(UUID payeeUserId, UUID tenantId, Pageable pageable);
    Page<Settlement> findAllByOrderIdAndTenantId(UUID orderId, UUID tenantId, Pageable pageable);
}
