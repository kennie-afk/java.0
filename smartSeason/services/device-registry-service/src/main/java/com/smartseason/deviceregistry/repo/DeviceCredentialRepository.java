package com.smartseason.deviceregistry.repo;

import com.smartseason.deviceregistry.domain.DeviceCredential;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceCredentialRepository extends JpaRepository<DeviceCredential, UUID>, JpaSpecificationExecutor<DeviceCredential> {

    Optional<DeviceCredential> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<DeviceCredential> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Slice<DeviceCredential> findAllByTenantIdOrderByCreatedAtDescIdDesc(UUID tenantId, Pageable pageable);

    @Query("SELECT e FROM DeviceCredential e WHERE e.tenantId = :tenantId "
            + "AND (e.createdAt < :cursorAt "
            + "     OR (e.createdAt = :cursorAt AND e.id < :cursorId)) "
            + "ORDER BY e.createdAt DESC, e.id DESC")
    Slice<DeviceCredential> findAfterCursor(@Param("tenantId") UUID tenantId,
                                  @Param("cursorAt") Instant cursorAt,
                                  @Param("cursorId") UUID cursorId,
                                  Pageable pageable);

    Page<DeviceCredential> findAllByDeviceIdAndTenantId(UUID deviceId, UUID tenantId, Pageable pageable);
    Optional<DeviceCredential> findByFingerprintAndTenantId(String fingerprint, UUID tenantId);
}
