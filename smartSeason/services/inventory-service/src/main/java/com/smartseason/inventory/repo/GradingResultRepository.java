package com.smartseason.inventory.repo;

import com.smartseason.inventory.domain.GradingResult;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GradingResultRepository extends JpaRepository<GradingResult, UUID> {

    Optional<GradingResult> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<GradingResult> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<GradingResult> findAllByBatchIdAndTenantId(UUID batchId, UUID tenantId, Pageable pageable);
}
