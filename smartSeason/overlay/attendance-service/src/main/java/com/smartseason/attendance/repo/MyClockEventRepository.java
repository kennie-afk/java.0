package com.smartseason.attendance.repo;

import com.smartseason.attendance.domain.ClockEvent;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Clock-event lookups scoped to one worker, separate from the generated
 * repository so a regeneration cannot drop them. See task-service's
 * MyWorkRepository for the same shape.
 */
@Repository
public interface MyClockEventRepository extends JpaRepository<ClockEvent, UUID> {

    List<ClockEvent> findAllByTenantIdAndWorkerIdOrderByOccurredAtDesc(UUID tenantId, UUID workerId);

    Optional<ClockEvent> findByIdAndTenantIdAndWorkerId(UUID id, UUID tenantId, UUID workerId);
}
