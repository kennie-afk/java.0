package com.smartseason.workforce.domain;

import com.smartseason.workforce.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "gangs", indexes = {
        @Index(name = "ix_gangs_farm_id", columnList = "farm_id"),
        @Index(name = "ix_gangs_supervisor_id", columnList = "supervisor_id")
})
public class Gang extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "supervisor_id")
    private UUID supervisorId;

    @Column(name = "target_size")
    private Integer targetSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public UUID getFarmId() { return farmId; }
    public void setFarmId(UUID farmId) { this.farmId = farmId; }

    public UUID getSupervisorId() { return supervisorId; }
    public void setSupervisorId(UUID supervisorId) { this.supervisorId = supervisorId; }

    public Integer getTargetSize() { return targetSize; }
    public void setTargetSize(Integer targetSize) { this.targetSize = targetSize; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public enum Status { ACTIVE, DISBANDED }

}
