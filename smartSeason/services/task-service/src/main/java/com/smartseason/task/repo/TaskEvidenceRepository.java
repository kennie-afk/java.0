package com.smartseason.task.repo;

import com.smartseason.task.domain.TaskEvidence;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TaskEvidenceRepository extends JpaRepository<TaskEvidence, UUID> {

    Optional<TaskEvidence> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<TaskEvidence> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<TaskEvidence> findAllByAssignmentIdAndTenantId(UUID assignmentId, UUID tenantId, Pageable pageable);
    Page<TaskEvidence> findAllByWorkOrderIdAndTenantId(UUID workOrderId, UUID tenantId, Pageable pageable);
    Page<TaskEvidence> findAllByPerceptualHashAndTenantId(String perceptualHash, UUID tenantId, Pageable pageable);
}
