package com.smartseason.inventory.repo;

import com.smartseason.inventory.domain.InputConsumption;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InputConsumptionRepository extends JpaRepository<InputConsumption, UUID> {

    Optional<InputConsumption> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<InputConsumption> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<InputConsumption> findAllByInputIssueIdAndTenantId(UUID inputIssueId, UUID tenantId, Pageable pageable);
    Page<InputConsumption> findAllByFarmIdAndTenantId(UUID farmId, UUID tenantId, Pageable pageable);
    Page<InputConsumption> findAllByPlotIdAndTenantId(UUID plotId, UUID tenantId, Pageable pageable);
    Page<InputConsumption> findAllBySeasonIdAndTenantId(UUID seasonId, UUID tenantId, Pageable pageable);
}
