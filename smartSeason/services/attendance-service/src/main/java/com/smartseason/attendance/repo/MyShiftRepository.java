package com.smartseason.attendance.repo;

import com.smartseason.attendance.domain.Shift;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Shift lookups scoped to one worker, separate from the generated repository
 * so a regeneration cannot drop them.
 */
@Repository
public interface MyShiftRepository extends JpaRepository<Shift, UUID> {

    List<Shift> findAllByTenantIdAndWorkerIdOrderByStartedAtDesc(UUID tenantId, UUID workerId);

    Optional<Shift> findByIdAndTenantIdAndWorkerId(UUID id, UUID tenantId, UUID workerId);
}
