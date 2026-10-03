package com.smartseason.weather.repo;

import com.smartseason.weather.domain.NdviReading;
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
public interface NdviReadingRepository extends JpaRepository<NdviReading, UUID>, JpaSpecificationExecutor<NdviReading> {

    Optional<NdviReading> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<NdviReading> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Slice<NdviReading> findAllByTenantIdOrderByCreatedAtDescIdDesc(UUID tenantId, Pageable pageable);

    @Query("SELECT e FROM NdviReading e WHERE e.tenantId = :tenantId "
            + "AND (e.createdAt < :cursorAt "
            + "     OR (e.createdAt = :cursorAt AND e.id < :cursorId)) "
            + "ORDER BY e.createdAt DESC, e.id DESC")
    Slice<NdviReading> findAfterCursor(@Param("tenantId") UUID tenantId,
                                  @Param("cursorAt") Instant cursorAt,
                                  @Param("cursorId") UUID cursorId,
                                  Pageable pageable);

    Page<NdviReading> findAllByPlotIdAndTenantId(UUID plotId, UUID tenantId, Pageable pageable);
    Page<NdviReading> findAllByGeoCellAndTenantId(String geoCell, UUID tenantId, Pageable pageable);
}
