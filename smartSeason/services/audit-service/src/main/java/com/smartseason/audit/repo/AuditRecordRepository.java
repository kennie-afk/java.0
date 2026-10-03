package com.smartseason.audit.repo;

import com.smartseason.audit.domain.AuditRecord;
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
public interface AuditRecordRepository extends JpaRepository<AuditRecord, UUID>, JpaSpecificationExecutor<AuditRecord> {

    Optional<AuditRecord> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<AuditRecord> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Slice<AuditRecord> findAllByTenantIdOrderByCreatedAtDescIdDesc(UUID tenantId, Pageable pageable);

    @Query("SELECT e FROM AuditRecord e WHERE e.tenantId = :tenantId "
            + "AND (e.createdAt < :cursorAt "
            + "     OR (e.createdAt = :cursorAt AND e.id < :cursorId)) "
            + "ORDER BY e.createdAt DESC, e.id DESC")
    Slice<AuditRecord> findAfterCursor(@Param("tenantId") UUID tenantId,
                                  @Param("cursorAt") Instant cursorAt,
                                  @Param("cursorId") UUID cursorId,
                                  Pageable pageable);

    Page<AuditRecord> findAllByServiceNameAndTenantId(String serviceName, UUID tenantId, Pageable pageable);
    Page<AuditRecord> findAllByActorUserIdAndTenantId(UUID actorUserId, UUID tenantId, Pageable pageable);
    Page<AuditRecord> findAllByActionAndTenantId(String action, UUID tenantId, Pageable pageable);
    Page<AuditRecord> findAllByResourceTypeAndTenantId(String resourceType, UUID tenantId, Pageable pageable);
    Page<AuditRecord> findAllByResourceIdAndTenantId(String resourceId, UUID tenantId, Pageable pageable);
    Optional<AuditRecord> findByRecordHashAndTenantId(String recordHash, UUID tenantId);
}
