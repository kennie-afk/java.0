package com.smartseason.agronomy.repo;

import com.smartseason.agronomy.domain.ScoutingReport;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Scouting-report lookups scoped to the worker who filed them, separate from
 * the generated repository so a regeneration cannot drop them.
 */
@Repository
public interface MyScoutingReportRepository extends JpaRepository<ScoutingReport, UUID> {

    List<ScoutingReport> findAllByTenantIdAndScoutedByOrderByScoutedAtDesc(UUID tenantId, UUID scoutedBy);

    Optional<ScoutingReport> findByIdAndTenantIdAndScoutedBy(UUID id, UUID tenantId, UUID scoutedBy);
}
