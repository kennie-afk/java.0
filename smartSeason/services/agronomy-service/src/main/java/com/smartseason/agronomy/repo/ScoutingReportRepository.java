package com.smartseason.agronomy.repo;

import com.smartseason.agronomy.domain.ScoutingReport;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ScoutingReportRepository extends JpaRepository<ScoutingReport, UUID> {

    Optional<ScoutingReport> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<ScoutingReport> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<ScoutingReport> findAllByPlotIdAndTenantId(UUID plotId, UUID tenantId, Pageable pageable);
    Page<ScoutingReport> findAllBySeasonIdAndTenantId(UUID seasonId, UUID tenantId, Pageable pageable);
}
