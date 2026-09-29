package com.smartseason.agronomy.myscouting;

import com.smartseason.agronomy.domain.ScoutingReport;
import com.smartseason.agronomy.platform.ResourceNotFoundException;
import com.smartseason.agronomy.platform.TenantContext;
import com.smartseason.agronomy.repo.MyScoutingReportRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The scouting reports one worker filed. Read-only: filing a report is a
 * separate, not-yet-built feature (see report-observation in tools/rbac.py)
 * - this closes the read-side gap, a worker being able to list every other
 * worker's reports via the generic catalogue screen. Same not-found-not-
 * forbidden shape as task-service's MyWorkService.
 */
@Service
@Transactional(readOnly = true)
public class MyScoutingService {

    private final MyScoutingReportRepository repository;

    public MyScoutingService(MyScoutingReportRepository repository) {
        this.repository = repository;
    }

    public List<ScoutingReport> mine(UUID callerUserId) {
        return repository.findAllByTenantIdAndScoutedByOrderByScoutedAtDesc(
                TenantContext.requireTenantId(), callerUserId);
    }

    public ScoutingReport one(UUID id, UUID callerUserId) {
        return repository.findByIdAndTenantIdAndScoutedBy(id, TenantContext.requireTenantId(), callerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("ScoutingReport", id));
    }
}
