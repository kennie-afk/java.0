package com.smartseason.analytics.repo;

import com.smartseason.analytics.domain.DashboardWidget;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DashboardWidgetRepository extends JpaRepository<DashboardWidget, UUID> {

    Optional<DashboardWidget> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<DashboardWidget> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<DashboardWidget> findAllByDashboardCodeAndTenantId(String dashboardCode, UUID tenantId, Pageable pageable);
}
