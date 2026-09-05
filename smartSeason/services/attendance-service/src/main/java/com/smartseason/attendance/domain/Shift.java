package com.smartseason.attendance.domain;

import com.smartseason.attendance.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "shifts", indexes = {
        @Index(name = "ix_shifts_worker_id", columnList = "worker_id"),
        @Index(name = "ix_shifts_farm_id", columnList = "farm_id"),
        @Index(name = "ix_shifts_gang_id", columnList = "gang_id")
})
public class Shift extends BaseEntity {

    @Column(name = "worker_id", nullable = false)
    private UUID workerId;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "gang_id")
    private UUID gangId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "break_minutes", nullable = false)
    private Integer breakMinutes;

    @Column(name = "supervisor_id")
    private UUID supervisorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "anomaly_flags")
    private String anomalyFlags;

    public UUID getWorkerId() { return workerId; }
    public void setWorkerId(UUID workerId) { this.workerId = workerId; }

    public UUID getFarmId() { return farmId; }
    public void setFarmId(UUID farmId) { this.farmId = farmId; }

    public UUID getGangId() { return gangId; }
    public void setGangId(UUID gangId) { this.gangId = gangId; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getEndedAt() { return endedAt; }
    public void setEndedAt(Instant endedAt) { this.endedAt = endedAt; }

    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }

    public Integer getBreakMinutes() { return breakMinutes; }
    public void setBreakMinutes(Integer breakMinutes) { this.breakMinutes = breakMinutes; }

    public UUID getSupervisorId() { return supervisorId; }
    public void setSupervisorId(UUID supervisorId) { this.supervisorId = supervisorId; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getAnomalyFlags() { return anomalyFlags; }
    public void setAnomalyFlags(String anomalyFlags) { this.anomalyFlags = anomalyFlags; }

    public enum Status { OPEN, CLOSED, DISPUTED }

}
