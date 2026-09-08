package com.smartseason.analytics.repo;

import com.smartseason.analytics.domain.MetricSnapshot;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MetricSnapshotRepository extends JpaRepository<MetricSnapshot, UUID> {

    Optional<MetricSnapshot> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<MetricSnapshot> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<MetricSnapshot> findAllByMetricKeyAndTenantId(String metricKey, UUID tenantId, Pageable pageable);
    Page<MetricSnapshot> findAllByDimensionAndTenantId(String dimension, UUID tenantId, Pageable pageable);
    Page<MetricSnapshot> findAllByDimensionValueAndTenantId(String dimensionValue, UUID tenantId, Pageable pageable);
}
