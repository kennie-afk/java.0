package com.smartseason.task.repo;

import com.smartseason.task.domain.TaskAssignment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Assignment lookups scoped to one person. Separate from the generated
 * repository so a regeneration cannot drop them.
 */
@Repository
public interface MyWorkRepository extends JpaRepository<TaskAssignment, UUID> {

    List<TaskAssignment> findAllByTenantIdAndWorkerUserIdOrderByAssignedAtDesc(
            UUID tenantId, UUID workerUserId);
}
