package com.smartseason.media.repo;

import com.smartseason.media.domain.MediaAsset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Media lookups scoped to the uploader, separate from the generated
 * repository so a regeneration cannot drop them.
 */
@Repository
public interface MyMediaRepository extends JpaRepository<MediaAsset, UUID> {

    List<MediaAsset> findAllByTenantIdAndOwnerUserIdOrderByCreatedAtDesc(UUID tenantId, UUID ownerUserId);

    Optional<MediaAsset> findByIdAndTenantIdAndOwnerUserId(UUID id, UUID tenantId, UUID ownerUserId);
}
