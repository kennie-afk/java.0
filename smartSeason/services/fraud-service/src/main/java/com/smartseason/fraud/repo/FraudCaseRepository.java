package com.smartseason.fraud.repo;

import com.smartseason.fraud.domain.FraudCase;
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
public interface FraudCaseRepository extends JpaRepository<FraudCase, UUID>, JpaSpecificationExecutor<FraudCase> {

    Optional<FraudCase> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<FraudCase> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Slice<FraudCase> findAllByTenantIdOrderByCreatedAtDescIdDesc(UUID tenantId, Pageable pageable);

    @Query("SELECT e FROM FraudCase e WHERE e.tenantId = :tenantId "
            + "AND (e.createdAt < :cursorAt "
            + "     OR (e.createdAt = :cursorAt AND e.id < :cursorId)) "
            + "ORDER BY e.createdAt DESC, e.id DESC")
    Slice<FraudCase> findAfterCursor(@Param("tenantId") UUID tenantId,
                                  @Param("cursorAt") Instant cursorAt,
                                  @Param("cursorId") UUID cursorId,
                                  Pageable pageable);

    Optional<FraudCase> findByCaseNumberAndTenantId(String caseNumber, UUID tenantId);
    Page<FraudCase> findAllBySubjectIdAndTenantId(UUID subjectId, UUID tenantId, Pageable pageable);
    Page<FraudCase> findAllByFarmIdAndTenantId(UUID farmId, UUID tenantId, Pageable pageable);
}
