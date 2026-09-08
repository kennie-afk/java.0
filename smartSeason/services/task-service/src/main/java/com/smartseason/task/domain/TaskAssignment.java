package com.smartseason.task.domain;

import com.smartseason.task.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_assignments", indexes = {
        @Index(name = "ix_task_assignments_work_order_id", columnList = "work_order_id"),
        @Index(name = "ix_task_assignments_worker_id", columnList = "worker_id"),
        @Index(name = "ix_task_assignments_worker_user_id", columnList = "worker_user_id"),
        @Index(name = "ix_task_assignments_gang_id", columnList = "gang_id")
})
public class TaskAssignment extends BaseEntity {

    @Column(name = "work_order_id", nullable = false)
    private UUID workOrderId;

    @Column(name = "worker_id")
    private UUID workerId;

    @Column(name = "worker_user_id")
    private UUID workerUserId;

    @Column(name = "gang_id")
    private UUID gangId;

    @Column(name = "assigned_by", nullable = false)
    private UUID assignedBy;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    public UUID getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(UUID workOrderId) { this.workOrderId = workOrderId; }

    public UUID getWorkerId() { return workerId; }
    public void setWorkerId(UUID workerId) { this.workerId = workerId; }

    public UUID getWorkerUserId() { return workerUserId; }
    public void setWorkerUserId(UUID workerUserId) { this.workerUserId = workerUserId; }

    public UUID getGangId() { return gangId; }
    public void setGangId(UUID gangId) { this.gangId = gangId; }

    public UUID getAssignedBy() { return assignedBy; }
    public void setAssignedBy(UUID assignedBy) { this.assignedBy = assignedBy; }

    public Instant getAssignedAt() { return assignedAt; }
    public void setAssignedAt(Instant assignedAt) { this.assignedAt = assignedAt; }

    public Instant getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(Instant acceptedAt) { this.acceptedAt = acceptedAt; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public enum Status { ASSIGNED, ACCEPTED, IN_PROGRESS, COMPLETED, REJECTED }

}
