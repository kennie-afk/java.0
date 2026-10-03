package com.smartseason.task.repo;

import com.smartseason.task.domain.TaskEvidence;
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
public interface TaskEvidenceRepository extends JpaRepository<TaskEvidence, UUID>, JpaSpecificationExecutor<TaskEvidence> {

    Optional<TaskEvidence> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<TaskEvidence> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Slice<TaskEvidence> findAllByTenantIdOrderByCreatedAtDescIdDesc(UUID tenantId, Pageable pageable);

    @Query("SELECT e FROM TaskEvidence e WHERE e.tenantId = :tenantId "
            + "AND (e.createdAt < :cursorAt "
            + "     OR (e.createdAt = :cursorAt AND e.id < :cursorId)) "
            + "ORDER BY e.createdAt DESC, e.id DESC")
    Slice<TaskEvidence> findAfterCursor(@Param("tenantId") UUID tenantId,
                                  @Param("cursorAt") Instant cursorAt,
                                  @Param("cursorId") UUID cursorId,
                                  Pageable pageable);

    Page<TaskEvidence> findAllByAssignmentIdAndTenantId(UUID assignmentId, UUID tenantId, Pageable pageable);
    Page<TaskEvidence> findAllByWorkOrderIdAndTenantId(UUID workOrderId, UUID tenantId, Pageable pageable);
    Page<TaskEvidence> findAllByPerceptualHashAndTenantId(String perceptualHash, UUID tenantId, Pageable pageable);
}
