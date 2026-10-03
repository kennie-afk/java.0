package com.smartseason.media.repo;

import com.smartseason.media.domain.MediaAsset;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MediaAssetRepository extends JpaRepository<MediaAsset, UUID>, JpaSpecificationExecutor<MediaAsset> {

    Optional<MediaAsset> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<MediaAsset> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Slice<MediaAsset> findAllByTenantIdOrderByCreatedAtDescIdDesc(UUID tenantId, Pageable pageable);

    @Query("SELECT e FROM MediaAsset e WHERE e.tenantId = :tenantId "
            + "AND (e.createdAt < :cursorAt "
            + "     OR (e.createdAt = :cursorAt AND e.id < :cursorId)) "
            + "ORDER BY e.createdAt DESC, e.id DESC")
    Slice<MediaAsset> findAfterCursor(@Param("tenantId") UUID tenantId,
                                  @Param("cursorAt") Instant cursorAt,
                                  @Param("cursorId") UUID cursorId,
                                  Pageable pageable);

    Optional<MediaAsset> findByStorageKeyAndTenantId(String storageKey, UUID tenantId);
    Page<MediaAsset> findAllByChecksumAndTenantId(String checksum, UUID tenantId, Pageable pageable);
    Page<MediaAsset> findAllByOwnerUserIdAndTenantId(UUID ownerUserId, UUID tenantId, Pageable pageable);
    Page<MediaAsset> findAllByContextAndTenantId(String context, UUID tenantId, Pageable pageable);
    Page<MediaAsset> findAllByContextRefAndTenantId(String contextRef, UUID tenantId, Pageable pageable);
    Page<MediaAsset> findAllByPerceptualHashAndTenantId(String perceptualHash, UUID tenantId, Pageable pageable);
}
