package com.smartseason.pricing.repo;

import com.smartseason.pricing.domain.PriceQuote;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PriceQuoteRepository extends JpaRepository<PriceQuote, UUID> {

    Optional<PriceQuote> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<PriceQuote> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<PriceQuote> findAllByCommodityCodeAndTenantId(String commodityCode, UUID tenantId, Pageable pageable);
}
