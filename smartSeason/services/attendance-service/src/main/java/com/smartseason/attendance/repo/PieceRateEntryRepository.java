package com.smartseason.attendance.repo;

import com.smartseason.attendance.domain.PieceRateEntry;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PieceRateEntryRepository extends JpaRepository<PieceRateEntry, UUID> {

    Optional<PieceRateEntry> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<PieceRateEntry> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<PieceRateEntry> findAllByWorkerIdAndTenantId(UUID workerId, UUID tenantId, Pageable pageable);
    Page<PieceRateEntry> findAllByShiftIdAndTenantId(UUID shiftId, UUID tenantId, Pageable pageable);
    Page<PieceRateEntry> findAllByFarmIdAndTenantId(UUID farmId, UUID tenantId, Pageable pageable);
    Page<PieceRateEntry> findAllByPlotIdAndTenantId(UUID plotId, UUID tenantId, Pageable pageable);
}
