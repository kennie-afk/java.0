package com.smartseason.media.repo;

import com.smartseason.media.domain.MediaAsset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID> {

    Optional<MediaAsset> findByIdAndTenantId(UUID id, UUID tenantId);

    Page<MediaAsset> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Optional<MediaAsset> findByStorageKeyAndTenantId(String storageKey, UUID tenantId);
    Page<MediaAsset> findAllByChecksumAndTenantId(String checksum, UUID tenantId, Pageable pageable);
    Page<MediaAsset> findAllByOwnerUserIdAndTenantId(UUID ownerUserId, UUID tenantId, Pageable pageable);
    Page<MediaAsset> findAllByContextAndTenantId(String context, UUID tenantId, Pageable pageable);
    Page<MediaAsset> findAllByContextRefAndTenantId(String contextRef, UUID tenantId, Pageable pageable);
    Page<MediaAsset> findAllByPerceptualHashAndTenantId(String perceptualHash, UUID tenantId, Pageable pageable);
}
