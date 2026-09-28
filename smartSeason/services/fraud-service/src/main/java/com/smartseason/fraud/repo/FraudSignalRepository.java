package com.smartseason.fraud.repo;

import com.smartseason.fraud.domain.FraudSignal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface FraudSignalRepository extends JpaRepository<FraudSignal, UUID> {

    Optional<FraudSignal> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<FraudSignal> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Slice<FraudSignal> findAllByTenantIdOrderByCreatedAtDescIdDesc(UUID tenantId, Pageable pageable);

    @Query("SELECT e FROM FraudSignal e WHERE e.tenantId = :tenantId "
            + "AND (e.createdAt < :cursorAt "
            + "     OR (e.createdAt = :cursorAt AND e.id < :cursorId)) "
            + "ORDER BY e.createdAt DESC, e.id DESC")
    Slice<FraudSignal> findAfterCursor(@Param("tenantId") UUID tenantId,
                                  @Param("cursorAt") Instant cursorAt,
                                  @Param("cursorId") UUID cursorId,
                                  Pageable pageable);

    Page<FraudSignal> findAllBySubjectIdAndTenantId(UUID subjectId, UUID tenantId, Pageable pageable);
    Page<FraudSignal> findAllByRuleCodeAndTenantId(String ruleCode, UUID tenantId, Pageable pageable);
    Page<FraudSignal> findAllByCaseIdAndTenantId(UUID caseId, UUID tenantId, Pageable pageable);
}
