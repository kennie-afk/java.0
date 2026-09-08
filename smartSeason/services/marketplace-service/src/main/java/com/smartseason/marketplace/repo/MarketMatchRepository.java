package com.smartseason.marketplace.repo;

import com.smartseason.marketplace.domain.MarketMatch;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MarketMatchRepository extends JpaRepository<MarketMatch, UUID> {

    Optional<MarketMatch> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<MarketMatch> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<MarketMatch> findAllByListingIdAndTenantId(UUID listingId, UUID tenantId, Pageable pageable);
    Page<MarketMatch> findAllByDemandPostIdAndTenantId(UUID demandPostId, UUID tenantId, Pageable pageable);
}
