package com.smartseason.traceability.repo;

import com.smartseason.traceability.domain.CertEvidence;
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
public interface CertEvidenceRepository extends JpaRepository<CertEvidence, UUID>, JpaSpecificationExecutor<CertEvidence> {

    Optional<CertEvidence> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<CertEvidence> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Slice<CertEvidence> findAllByTenantIdOrderByCreatedAtDescIdDesc(UUID tenantId, Pageable pageable);

    @Query("SELECT e FROM CertEvidence e WHERE e.tenantId = :tenantId "
            + "AND (e.createdAt < :cursorAt "
            + "     OR (e.createdAt = :cursorAt AND e.id < :cursorId)) "
            + "ORDER BY e.createdAt DESC, e.id DESC")
    Slice<CertEvidence> findAfterCursor(@Param("tenantId") UUID tenantId,
                                  @Param("cursorAt") Instant cursorAt,
                                  @Param("cursorId") UUID cursorId,
                                  Pageable pageable);

    Page<CertEvidence> findAllByBatchCodeAndTenantId(String batchCode, UUID tenantId, Pageable pageable);
    Page<CertEvidence> findAllByFarmIdAndTenantId(UUID farmId, UUID tenantId, Pageable pageable);
}
