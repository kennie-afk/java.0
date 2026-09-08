package com.smartseason.agronomy.repo;

import com.smartseason.agronomy.domain.Advisory;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdvisoryRepository extends JpaRepository<Advisory, UUID> {

    Optional<Advisory> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<Advisory> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<Advisory> findAllBySeasonIdAndTenantId(UUID seasonId, UUID tenantId, Pageable pageable);
    Page<Advisory> findAllByPlotIdAndTenantId(UUID plotId, UUID tenantId, Pageable pageable);
    Page<Advisory> findAllByCropCodeAndTenantId(String cropCode, UUID tenantId, Pageable pageable);
}
