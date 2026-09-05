package com.smartseason.task.repo;

import com.smartseason.task.domain.TaskAssignment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TaskAssignmentRepository extends JpaRepository<TaskAssignment, UUID> {

    Optional<TaskAssignment> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<TaskAssignment> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<TaskAssignment> findAllByWorkOrderIdAndTenantId(UUID workOrderId, UUID tenantId, Pageable pageable);
    Page<TaskAssignment> findAllByWorkerIdAndTenantId(UUID workerId, UUID tenantId, Pageable pageable);
    Page<TaskAssignment> findAllByGangIdAndTenantId(UUID gangId, UUID tenantId, Pageable pageable);
}
