package com.smartseason.workforce.repo;

import com.smartseason.workforce.domain.WageRate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WageRateRepository extends JpaRepository<WageRate, UUID> {

    Optional<WageRate> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<WageRate> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<WageRate> findAllByFarmIdAndTenantId(UUID farmId, UUID tenantId, Pageable pageable);
    Page<WageRate> findAllByTaskCodeAndTenantId(String taskCode, UUID tenantId, Pageable pageable);
}
