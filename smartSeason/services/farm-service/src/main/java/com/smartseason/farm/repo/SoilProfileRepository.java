package com.smartseason.farm.repo;

import com.smartseason.farm.domain.SoilProfile;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SoilProfileRepository extends JpaRepository<SoilProfile, UUID> {

    Optional<SoilProfile> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<SoilProfile> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<SoilProfile> findAllByPlotIdAndTenantId(UUID plotId, UUID tenantId, Pageable pageable);
}
