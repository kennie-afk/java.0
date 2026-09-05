package com.smartseason.telemetryingest.repo;

import com.smartseason.telemetryingest.domain.TelemetryAnomalyRecord;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TelemetryAnomalyRecordRepository extends JpaRepository<TelemetryAnomalyRecord, UUID> {

    Optional<TelemetryAnomalyRecord> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<TelemetryAnomalyRecord> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<TelemetryAnomalyRecord> findAllByDeviceIdAndTenantId(UUID deviceId, UUID tenantId, Pageable pageable);
    Page<TelemetryAnomalyRecord> findAllByPlotIdAndTenantId(UUID plotId, UUID tenantId, Pageable pageable);
}
