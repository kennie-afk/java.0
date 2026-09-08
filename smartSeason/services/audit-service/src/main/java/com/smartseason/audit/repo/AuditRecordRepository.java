package com.smartseason.audit.repo;

import com.smartseason.audit.domain.AuditRecord;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditRecordRepository extends JpaRepository<AuditRecord, UUID> {

    Optional<AuditRecord> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<AuditRecord> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<AuditRecord> findAllByServiceNameAndTenantId(String serviceName, UUID tenantId, Pageable pageable);
    Page<AuditRecord> findAllByActorUserIdAndTenantId(UUID actorUserId, UUID tenantId, Pageable pageable);
    Page<AuditRecord> findAllByActionAndTenantId(String action, UUID tenantId, Pageable pageable);
    Page<AuditRecord> findAllByResourceTypeAndTenantId(String resourceType, UUID tenantId, Pageable pageable);
    Page<AuditRecord> findAllByResourceIdAndTenantId(String resourceId, UUID tenantId, Pageable pageable);
    Optional<AuditRecord> findByRecordHashAndTenantId(String recordHash, UUID tenantId);
}
