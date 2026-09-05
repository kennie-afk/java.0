package com.smartseason.farm.repo;

import com.smartseason.farm.domain.FarmMembership;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FarmMembershipRepository extends JpaRepository<FarmMembership, UUID> {

    Optional<FarmMembership> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<FarmMembership> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<FarmMembership> findAllByFarmIdAndTenantId(UUID farmId, UUID tenantId, Pageable pageable);
    Page<FarmMembership> findAllByUserIdAndTenantId(UUID userId, UUID tenantId, Pageable pageable);
}
