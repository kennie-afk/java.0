package com.smartseason.marketplace.repo;

import com.smartseason.marketplace.domain.SupplyListing;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SupplyListingRepository extends JpaRepository<SupplyListing, UUID> {

    Optional<SupplyListing> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<SupplyListing> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<SupplyListing> findAllBySellerOrgIdAndTenantId(UUID sellerOrgId, UUID tenantId, Pageable pageable);
    Page<SupplyListing> findAllByFarmIdAndTenantId(UUID farmId, UUID tenantId, Pageable pageable);
    Page<SupplyListing> findAllByCommodityCodeAndTenantId(String commodityCode, UUID tenantId, Pageable pageable);
    Page<SupplyListing> findAllByCountyAndTenantId(String county, UUID tenantId, Pageable pageable);
}
