package com.smartseason.season.repo;

import com.smartseason.season.domain.SeasonStage;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SeasonStageRepository extends JpaRepository<SeasonStage, UUID> {

    Optional<SeasonStage> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<SeasonStage> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Page<SeasonStage> findAllBySeasonIdAndTenantId(UUID seasonId, UUID tenantId, Pageable pageable);
}
