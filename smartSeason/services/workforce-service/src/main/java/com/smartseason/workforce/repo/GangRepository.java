package com.smartseason.workforce.repo;

import com.smartseason.workforce.domain.Gang;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GangRepository extends JpaRepository<Gang, UUID> {

    Optional<Gang> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<Gang> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<Gang> findAllByFarmIdAndTenantId(UUID farmId, UUID tenantId, Pageable pageable);
    Page<Gang> findAllBySupervisorIdAndTenantId(UUID supervisorId, UUID tenantId, Pageable pageable);
}
