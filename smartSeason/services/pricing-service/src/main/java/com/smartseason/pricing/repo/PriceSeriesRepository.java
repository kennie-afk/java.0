package com.smartseason.pricing.repo;

import com.smartseason.pricing.domain.PriceSeries;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PriceSeriesRepository extends JpaRepository<PriceSeries, UUID> {

    Optional<PriceSeries> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<PriceSeries> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<PriceSeries> findAllByCommodityCodeAndTenantId(String commodityCode, UUID tenantId, Pageable pageable);
    Page<PriceSeries> findAllByCountyAndTenantId(String county, UUID tenantId, Pageable pageable);
}
