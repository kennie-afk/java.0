package com.smartseason.attendance.repo;

import com.smartseason.attendance.domain.Geofence;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GeofenceRepository extends JpaRepository<Geofence, UUID> {

    Optional<Geofence> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<Geofence> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<Geofence> findAllByFarmIdAndTenantId(UUID farmId, UUID tenantId, Pageable pageable);
    Page<Geofence> findAllByPlotIdAndTenantId(UUID plotId, UUID tenantId, Pageable pageable);
}
