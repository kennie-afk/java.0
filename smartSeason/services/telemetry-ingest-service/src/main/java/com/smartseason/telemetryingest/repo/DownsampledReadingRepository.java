package com.smartseason.telemetryingest.repo;

import com.smartseason.telemetryingest.domain.DownsampledReading;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DownsampledReadingRepository extends JpaRepository<DownsampledReading, UUID> {

    Optional<DownsampledReading> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<DownsampledReading> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<DownsampledReading> findAllByDeviceIdAndTenantId(UUID deviceId, UUID tenantId, Pageable pageable);
}
