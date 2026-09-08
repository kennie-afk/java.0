package com.smartseason.workforce.repo;

import com.smartseason.workforce.domain.WorkerContract;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WorkerContractRepository extends JpaRepository<WorkerContract, UUID> {

    Optional<WorkerContract> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<WorkerContract> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<WorkerContract> findAllByWorkerIdAndTenantId(UUID workerId, UUID tenantId, Pageable pageable);
    Page<WorkerContract> findAllByFarmIdAndTenantId(UUID farmId, UUID tenantId, Pageable pageable);
}
