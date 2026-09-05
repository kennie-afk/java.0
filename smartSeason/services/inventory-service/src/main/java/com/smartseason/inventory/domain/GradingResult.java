package com.smartseason.inventory.domain;

import com.smartseason.inventory.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "grading_results", indexes = {
        @Index(name = "ix_grading_results_batch_id", columnList = "batch_id")
})
public class GradingResult extends BaseEntity {

    @Column(name = "batch_id", nullable = false)
    private UUID batchId;

    @Column(name = "graded_by")
    private UUID gradedBy;

    @Column(name = "graded_at", nullable = false)
    private Instant gradedAt;

    @Column(name = "assigned_grade", nullable = false)
    private String assignedGrade;

    @Column(name = "size_mm")
    private BigDecimal sizeMm;

    @Column(name = "defect_pct")
    private BigDecimal defectPct;

    @Column(name = "moisture_pct")
    private BigDecimal moisturePct;

    @Column(name = "rejected_kg")
    private BigDecimal rejectedKg;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "standard_version")
    private Integer standardVersion;

    public UUID getBatchId() { return batchId; }
    public void setBatchId(UUID batchId) { this.batchId = batchId; }

    public UUID getGradedBy() { return gradedBy; }
    public void setGradedBy(UUID gradedBy) { this.gradedBy = gradedBy; }

    public Instant getGradedAt() { return gradedAt; }
    public void setGradedAt(Instant gradedAt) { this.gradedAt = gradedAt; }

    public String getAssignedGrade() { return assignedGrade; }
    public void setAssignedGrade(String assignedGrade) { this.assignedGrade = assignedGrade; }

    public BigDecimal getSizeMm() { return sizeMm; }
    public void setSizeMm(BigDecimal sizeMm) { this.sizeMm = sizeMm; }

    public BigDecimal getDefectPct() { return defectPct; }
    public void setDefectPct(BigDecimal defectPct) { this.defectPct = defectPct; }

    public BigDecimal getMoisturePct() { return moisturePct; }
    public void setMoisturePct(BigDecimal moisturePct) { this.moisturePct = moisturePct; }

    public BigDecimal getRejectedKg() { return rejectedKg; }
    public void setRejectedKg(BigDecimal rejectedKg) { this.rejectedKg = rejectedKg; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Integer getStandardVersion() { return standardVersion; }
    public void setStandardVersion(Integer standardVersion) { this.standardVersion = standardVersion; }

}
