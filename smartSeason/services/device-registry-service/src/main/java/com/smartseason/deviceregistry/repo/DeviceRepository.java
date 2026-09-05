package com.smartseason.deviceregistry.repo;

import com.smartseason.deviceregistry.domain.Device;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceRepository extends JpaRepository<Device, UUID> {

    Optional<Device> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<Device> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Optional<Device> findBySerialNumberAndTenantId(String serialNumber, UUID tenantId);
    Page<Device> findAllByPlotIdAndTenantId(UUID plotId, UUID tenantId, Pageable pageable);
    Page<Device> findAllByFarmIdAndTenantId(UUID farmId, UUID tenantId, Pageable pageable);
}
