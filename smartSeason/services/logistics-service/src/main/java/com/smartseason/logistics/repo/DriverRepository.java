package com.smartseason.logistics.repo;

import com.smartseason.logistics.domain.Driver;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DriverRepository extends JpaRepository<Driver, UUID> {

    Optional<Driver> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<Driver> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<Driver> findAllByUserIdAndTenantId(UUID userId, UUID tenantId, Pageable pageable);
    Page<Driver> findAllByPhoneAndTenantId(String phone, UUID tenantId, Pageable pageable);
    Optional<Driver> findByLicenceNumberAndTenantId(String licenceNumber, UUID tenantId);
    Page<Driver> findAllByAssignedVehicleIdAndTenantId(UUID assignedVehicleId, UUID tenantId, Pageable pageable);
}
