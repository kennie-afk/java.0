package com.smartseason.task.mywork;

import com.smartseason.task.domain.TaskAssignment;
import com.smartseason.task.platform.DomainRuleException;
import com.smartseason.task.platform.ResourceNotFoundException;
import com.smartseason.task.platform.TenantContext;
import com.smartseason.task.repo.MyWorkRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Starting and stopping work.
 *
 * Two things are deliberately not negotiable here, because this is the feature
 * that exists to stop hours being inflated:
 *
 *   1. The time is taken from the server clock. No caller supplies it, so a
 *      changed phone clock changes nothing.
 *   2. A worker may only start or stop an assignment that is theirs. A
 *      supervisor may act on any assignment in the tenant, and that difference
 *      is decided here rather than in the browser.
 *
 * The generic PATCH endpoint can still set these columns, which is why writes on
 * task-service are restricted to FARMER and MANAGER: a worker holds OWN, so
 * these endpoints are their only way to record time.
 */
@Service
@Transactional(readOnly = true)
public class MyWorkService {

    private final MyWorkRepository repository;

    public MyWorkService(MyWorkRepository repository) {
        this.repository = repository;
    }

    public List<TaskAssignment> mine(UUID callerUserId) {
        return repository.findAllByTenantIdAndWorkerUserIdOrderByAssignedAtDesc(
                TenantContext.requireTenantId(), callerUserId);
    }

    @Transactional
    public TaskAssignment start(UUID assignmentId, UUID callerUserId, boolean supervising) {
        TaskAssignment assignment = require(assignmentId, callerUserId, supervising);

        if (assignment.getCompletedAt() != null) {
            throw new DomainRuleException("That task has already been finished");
        }
        if (assignment.getStartedAt() != null) {
            throw new DomainRuleException("That task is already running");
        }

        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        if (assignment.getAcceptedAt() == null) {
            assignment.setAcceptedAt(now);
        }
        assignment.setStartedAt(now);
        assignment.setStatus(TaskAssignment.Status.IN_PROGRESS);
        return repository.save(assignment);
    }

    @Transactional
    public TaskAssignment stop(UUID assignmentId, UUID callerUserId, boolean supervising) {
        TaskAssignment assignment = require(assignmentId, callerUserId, supervising);

        if (assignment.getStartedAt() == null) {
            throw new DomainRuleException("That task was never started");
        }
        if (assignment.getCompletedAt() != null) {
            throw new DomainRuleException("That task has already been finished");
        }

        assignment.setCompletedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
        assignment.setStatus(TaskAssignment.Status.COMPLETED);
        return repository.save(assignment);
    }

    private TaskAssignment require(UUID assignmentId, UUID callerUserId, boolean supervising) {
        TaskAssignment assignment = repository.findById(assignmentId)
                .filter(found -> TenantContext.requireTenantId().equals(found.getTenantId()))
                .orElseThrow(() -> new ResourceNotFoundException("TaskAssignment", assignmentId));

        if (!supervising && !callerUserId.equals(assignment.getWorkerUserId())) {
            // Reported as not-found rather than forbidden: confirming that a
            // particular assignment exists is itself information a worker should
            // not get about other people's work.
            throw new ResourceNotFoundException("TaskAssignment", assignmentId);
        }
        return assignment;
    }
}
