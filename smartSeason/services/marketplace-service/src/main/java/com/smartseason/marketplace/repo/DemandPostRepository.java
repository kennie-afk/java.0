package com.smartseason.marketplace.repo;

import com.smartseason.marketplace.domain.DemandPost;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DemandPostRepository extends JpaRepository<DemandPost, UUID> {

    Optional<DemandPost> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<DemandPost> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<DemandPost> findAllByBuyerOrgIdAndTenantId(UUID buyerOrgId, UUID tenantId, Pageable pageable);
    Page<DemandPost> findAllByCommodityCodeAndTenantId(String commodityCode, UUID tenantId, Pageable pageable);
    Page<DemandPost> findAllByDeliveryCountyAndTenantId(String deliveryCounty, UUID tenantId, Pageable pageable);
}
