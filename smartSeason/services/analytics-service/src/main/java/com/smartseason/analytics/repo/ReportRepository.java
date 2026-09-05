package com.smartseason.analytics.repo;

import com.smartseason.analytics.domain.Report;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportRepository extends JpaRepository<Report, UUID> {

    Optional<Report> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<Report> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Report> findByCodeAndTenantId(String code, UUID tenantId);
    Page<Report> findAllByCategoryAndTenantId(String category, UUID tenantId, Pageable pageable);
}
