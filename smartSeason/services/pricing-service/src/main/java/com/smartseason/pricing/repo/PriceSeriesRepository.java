package com.smartseason.pricing.repo;

import com.smartseason.pricing.domain.PriceSeries;
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
public interface PriceSeriesRepository extends JpaRepository<PriceSeries, UUID>, JpaSpecificationExecutor<PriceSeries> {

    Optional<PriceSeries> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<PriceSeries> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Slice<PriceSeries> findAllByTenantIdOrderByCreatedAtDescIdDesc(UUID tenantId, Pageable pageable);

    @Query("SELECT e FROM PriceSeries e WHERE e.tenantId = :tenantId "
            + "AND (e.createdAt < :cursorAt "
            + "     OR (e.createdAt = :cursorAt AND e.id < :cursorId)) "
            + "ORDER BY e.createdAt DESC, e.id DESC")
    Slice<PriceSeries> findAfterCursor(@Param("tenantId") UUID tenantId,
                                  @Param("cursorAt") Instant cursorAt,
                                  @Param("cursorId") UUID cursorId,
                                  Pageable pageable);

    Page<PriceSeries> findAllByCommodityCodeAndTenantId(String commodityCode, UUID tenantId, Pageable pageable);
    Page<PriceSeries> findAllByCountyAndTenantId(String county, UUID tenantId, Pageable pageable);
}
