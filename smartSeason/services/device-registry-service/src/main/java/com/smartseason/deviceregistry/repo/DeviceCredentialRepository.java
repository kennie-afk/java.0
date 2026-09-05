package com.smartseason.deviceregistry.repo;

import com.smartseason.deviceregistry.domain.DeviceCredential;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceCredentialRepository extends JpaRepository<DeviceCredential, UUID> {

    Optional<DeviceCredential> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<DeviceCredential> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<DeviceCredential> findAllByDeviceIdAndTenantId(UUID deviceId, UUID tenantId, Pageable pageable);
    Optional<DeviceCredential> findByFingerprintAndTenantId(String fingerprint, UUID tenantId);
}
