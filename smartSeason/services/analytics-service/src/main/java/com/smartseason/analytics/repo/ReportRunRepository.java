package com.smartseason.analytics.repo;

import com.smartseason.analytics.domain.ReportRun;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportRunRepository extends JpaRepository<ReportRun, UUID> {

    Optional<ReportRun> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<ReportRun> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<ReportRun> findAllByReportIdAndTenantId(UUID reportId, UUID tenantId, Pageable pageable);
    Page<ReportRun> findAllByReportCodeAndTenantId(String reportCode, UUID tenantId, Pageable pageable);
}
