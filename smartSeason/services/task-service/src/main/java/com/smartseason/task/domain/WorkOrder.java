package com.smartseason.task.domain;

import com.smartseason.task.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "work_orders", indexes = {
        @Index(name = "ix_work_orders_farm_id", columnList = "farm_id"),
        @Index(name = "ix_work_orders_plot_id", columnList = "plot_id"),
        @Index(name = "ix_work_orders_season_id", columnList = "season_id")
})
public class WorkOrder extends BaseEntity {

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "plot_id")
    private UUID plotId;

    @Column(name = "season_id")
    private UUID seasonId;

    @Column(name = "task_code", nullable = false)
    private String taskCode;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false)
    private Priority priority;

    @Column(name = "estimated_hours")
    private BigDecimal estimatedHours;

    @Column(name = "created_by")
    private UUID createdBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    public UUID getFarmId() { return farmId; }
    public void setFarmId(UUID farmId) { this.farmId = farmId; }

    public UUID getPlotId() { return plotId; }
    public void setPlotId(UUID plotId) { this.plotId = plotId; }

    public UUID getSeasonId() { return seasonId; }
    public void setSeasonId(UUID seasonId) { this.seasonId = seasonId; }

    public String getTaskCode() { return taskCode; }
    public void setTaskCode(String taskCode) { this.taskCode = taskCode; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public Priority getPriority() { return priority; }
    public void setPriority(Priority priority) { this.priority = priority; }

    public BigDecimal getEstimatedHours() { return estimatedHours; }
    public void setEstimatedHours(BigDecimal estimatedHours) { this.estimatedHours = estimatedHours; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public enum Priority { LOW, NORMAL, HIGH, URGENT }

    public enum Status { DRAFT, OPEN, IN_PROGRESS, COMPLETED, VERIFIED, CANCELLED }

}
