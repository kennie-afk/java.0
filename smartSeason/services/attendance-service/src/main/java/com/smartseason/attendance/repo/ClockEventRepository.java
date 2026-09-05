package com.smartseason.attendance.repo;

import com.smartseason.attendance.domain.ClockEvent;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClockEventRepository extends JpaRepository<ClockEvent, UUID> {

    Optional<ClockEvent> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<ClockEvent> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<ClockEvent> findAllByWorkerIdAndTenantId(UUID workerId, UUID tenantId, Pageable pageable);
    Page<ClockEvent> findAllByFarmIdAndTenantId(UUID farmId, UUID tenantId, Pageable pageable);
    Page<ClockEvent> findAllByShiftIdAndTenantId(UUID shiftId, UUID tenantId, Pageable pageable);
    Optional<ClockEvent> findByClientEventIdAndTenantId(String clientEventId, UUID tenantId);
}
