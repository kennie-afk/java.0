package com.smartseason.marketplace.repo;

import com.smartseason.marketplace.domain.Offer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OfferRepository extends JpaRepository<Offer, UUID> {

    Optional<Offer> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<Offer> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<Offer> findAllByListingIdAndTenantId(UUID listingId, UUID tenantId, Pageable pageable);
    Page<Offer> findAllByDemandPostIdAndTenantId(UUID demandPostId, UUID tenantId, Pageable pageable);
    Page<Offer> findAllByFromOrgIdAndTenantId(UUID fromOrgId, UUID tenantId, Pageable pageable);
    Page<Offer> findAllByToOrgIdAndTenantId(UUID toOrgId, UUID tenantId, Pageable pageable);
}
