package com.smartseason.payout.repo;

import com.smartseason.payout.domain.PayoutBatch;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PayoutBatchRepository extends JpaRepository<PayoutBatch, UUID> {

    Optional<PayoutBatch> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<PayoutBatch> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Optional<PayoutBatch> findByBatchNumberAndTenantId(String batchNumber, UUID tenantId);
    Page<PayoutBatch> findAllByFarmIdAndTenantId(UUID farmId, UUID tenantId, Pageable pageable);
}
