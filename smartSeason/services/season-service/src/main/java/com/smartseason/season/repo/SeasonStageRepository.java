package com.smartseason.season.repo;

import com.smartseason.season.domain.SeasonStage;
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
public interface SeasonStageRepository extends JpaRepository<SeasonStage, UUID>, JpaSpecificationExecutor<SeasonStage> {

    Optional<SeasonStage> findByIdAndTenantId(UUID id, UUID tenantId);

    Slice<SeasonStage> findAllByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByIdAndTenantId(UUID id, UUID tenantId);

    long countByTenantId(UUID tenantId);

    void deleteByIdAndTenantId(UUID id, UUID tenantId);

    Slice<SeasonStage> findAllByTenantIdOrderByCreatedAtDescIdDesc(UUID tenantId, Pageable pageable);

    @Query("SELECT e FROM SeasonStage e WHERE e.tenantId = :tenantId "
            + "AND (e.createdAt < :cursorAt "
            + "     OR (e.createdAt = :cursorAt AND e.id < :cursorId)) "
            + "ORDER BY e.createdAt DESC, e.id DESC")
    Slice<SeasonStage> findAfterCursor(@Param("tenantId") UUID tenantId,
                                  @Param("cursorAt") Instant cursorAt,
                                  @Param("cursorId") UUID cursorId,
                                  Pageable pageable);

    Page<SeasonStage> findAllBySeasonIdAndTenantId(UUID seasonId, UUID tenantId, Pageable pageable);
}
