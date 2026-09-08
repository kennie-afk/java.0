package com.smartseason.telemetryingest.repo;

import com.smartseason.telemetryingest.domain.TelemetryReading;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TelemetryReadingRepository extends JpaRepository<TelemetryReading, UUID> {

    Optional<TelemetryReading> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<TelemetryReading> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<TelemetryReading> findAllByDeviceIdAndTenantId(UUID deviceId, UUID tenantId, Pageable pageable);
    Page<TelemetryReading> findAllByPlotIdAndTenantId(UUID plotId, UUID tenantId, Pageable pageable);
    Page<TelemetryReading> findAllByMetricAndTenantId(String metric, UUID tenantId, Pageable pageable);
}
