package com.smartseason.farm.repo;

import com.smartseason.farm.domain.Farm;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FarmRepository extends JpaRepository<Farm, UUID> {

    Optional<Farm> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<Farm> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<Farm> findAllByOwnerUserIdAndTenantId(UUID ownerUserId, UUID tenantId, Pageable pageable);
    Page<Farm> findAllByCountyAndTenantId(String county, UUID tenantId, Pageable pageable);
    Page<Farm> findAllByCooperativeIdAndTenantId(UUID cooperativeId, UUID tenantId, Pageable pageable);
}
