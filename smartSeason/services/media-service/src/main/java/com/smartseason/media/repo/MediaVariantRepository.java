package com.smartseason.media.repo;

import com.smartseason.media.domain.MediaVariant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaVariantRepository extends JpaRepository<MediaVariant, UUID> {

    Optional<MediaVariant> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<MediaVariant> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<MediaVariant> findAllByAssetIdAndTenantId(UUID assetId, UUID tenantId, Pageable pageable);
}
