package com.smartseason.pricing.repo;

import com.smartseason.pricing.domain.MarketIndex;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MarketIndexRepository extends JpaRepository<MarketIndex, UUID> {

    Optional<MarketIndex> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<MarketIndex> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<MarketIndex> findAllByCommodityCodeAndTenantId(String commodityCode, UUID tenantId, Pageable pageable);
    Page<MarketIndex> findAllByRegionAndTenantId(String region, UUID tenantId, Pageable pageable);
}
