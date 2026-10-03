package com.smartseason.inventory.repo;

import com.smartseason.inventory.domain.InputIssue;
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
public interface InputIssueRepository extends JpaRepository<InputIssue, UUID>, JpaSpecificationExecutor<InputIssue> {

    Optional<InputIssue> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<InputIssue> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Slice<InputIssue> findAllByTenantIdOrderByCreatedAtDescIdDesc(UUID tenantId, Pageable pageable);

    @Query("SELECT e FROM InputIssue e WHERE e.tenantId = :tenantId "
            + "AND (e.createdAt < :cursorAt "
            + "     OR (e.createdAt = :cursorAt AND e.id < :cursorId)) "
            + "ORDER BY e.createdAt DESC, e.id DESC")
    Slice<InputIssue> findAfterCursor(@Param("tenantId") UUID tenantId,
                                  @Param("cursorAt") Instant cursorAt,
                                  @Param("cursorId") UUID cursorId,
                                  Pageable pageable);

    Page<InputIssue> findAllByFarmIdAndTenantId(UUID farmId, UUID tenantId, Pageable pageable);
    Page<InputIssue> findAllByPlotIdAndTenantId(UUID plotId, UUID tenantId, Pageable pageable);
    Page<InputIssue> findAllBySeasonIdAndTenantId(UUID seasonId, UUID tenantId, Pageable pageable);
    Page<InputIssue> findAllByInputCodeAndTenantId(String inputCode, UUID tenantId, Pageable pageable);
}
