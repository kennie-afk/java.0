package com.smartseason.attendance.repo;

import com.smartseason.attendance.domain.PieceRateEntry;
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
public interface PieceRateEntryRepository extends JpaRepository<PieceRateEntry, UUID>, JpaSpecificationExecutor<PieceRateEntry> {

    Optional<PieceRateEntry> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<PieceRateEntry> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Slice<PieceRateEntry> findAllByTenantIdOrderByCreatedAtDescIdDesc(UUID tenantId, Pageable pageable);

    @Query("SELECT e FROM PieceRateEntry e WHERE e.tenantId = :tenantId "
            + "AND (e.createdAt < :cursorAt "
            + "     OR (e.createdAt = :cursorAt AND e.id < :cursorId)) "
            + "ORDER BY e.createdAt DESC, e.id DESC")
    Slice<PieceRateEntry> findAfterCursor(@Param("tenantId") UUID tenantId,
                                  @Param("cursorAt") Instant cursorAt,
                                  @Param("cursorId") UUID cursorId,
                                  Pageable pageable);

    Page<PieceRateEntry> findAllByWorkerIdAndTenantId(UUID workerId, UUID tenantId, Pageable pageable);
    Page<PieceRateEntry> findAllByShiftIdAndTenantId(UUID shiftId, UUID tenantId, Pageable pageable);
    Page<PieceRateEntry> findAllByFarmIdAndTenantId(UUID farmId, UUID tenantId, Pageable pageable);
    Page<PieceRateEntry> findAllByPlotIdAndTenantId(UUID plotId, UUID tenantId, Pageable pageable);
}
