package com.smartseason.fraud.repo;

import com.smartseason.fraud.domain.WorkerRiskScore;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkerRiskScoreRepository extends JpaRepository<WorkerRiskScore, UUID> {

    Optional<WorkerRiskScore> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<WorkerRiskScore> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Optional<WorkerRiskScore> findByWorkerIdAndTenantId(UUID workerId, UUID tenantId);
    Page<WorkerRiskScore> findAllByFarmIdAndTenantId(UUID farmId, UUID tenantId, Pageable pageable);
}
