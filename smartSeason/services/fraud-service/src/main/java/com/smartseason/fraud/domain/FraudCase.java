package com.smartseason.fraud.domain;

import com.smartseason.fraud.platform.BaseEntity;
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
@Table(name = "fraud_cases", indexes = {
        @Index(name = "ix_fraud_cases_case_number", columnList = "case_number"),
        @Index(name = "ix_fraud_cases_subject_id", columnList = "subject_id"),
        @Index(name = "ix_fraud_cases_farm_id", columnList = "farm_id")
})
public class FraudCase extends BaseEntity {

    @Column(name = "case_number", nullable = false, unique = true)
    private String caseNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false)
    private SubjectType subjectType;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "farm_id")
    private UUID farmId;

    @Column(name = "typology", nullable = false)
    private String typology;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private Severity severity;

    @Column(name = "confidence", nullable = false)
    private BigDecimal confidence;

    @Column(name = "opened_at", nullable = false)
    private Instant openedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "assigned_to")
    private UUID assignedTo;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution", columnDefinition = "TEXT")
    private String resolution;

    @Column(name = "payout_held", nullable = false)
    private Boolean payoutHeld;

    @Column(name = "appealed_at")
    private Instant appealedAt;

    @Column(name = "appeal_outcome")
    private String appealOutcome;

    public String getCaseNumber() { return caseNumber; }
    public void setCaseNumber(String caseNumber) { this.caseNumber = caseNumber; }

    public SubjectType getSubjectType() { return subjectType; }
    public void setSubjectType(SubjectType subjectType) { this.subjectType = subjectType; }

    public UUID getSubjectId() { return subjectId; }
    public void setSubjectId(UUID subjectId) { this.subjectId = subjectId; }

    public UUID getFarmId() { return farmId; }
    public void setFarmId(UUID farmId) { this.farmId = farmId; }

    public String getTypology() { return typology; }
    public void setTypology(String typology) { this.typology = typology; }

    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }

    public BigDecimal getConfidence() { return confidence; }
    public void setConfidence(BigDecimal confidence) { this.confidence = confidence; }

    public Instant getOpenedAt() { return openedAt; }
    public void setOpenedAt(Instant openedAt) { this.openedAt = openedAt; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public UUID getAssignedTo() { return assignedTo; }
    public void setAssignedTo(UUID assignedTo) { this.assignedTo = assignedTo; }

    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }

    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }

    public Boolean getPayoutHeld() { return payoutHeld; }
    public void setPayoutHeld(Boolean payoutHeld) { this.payoutHeld = payoutHeld; }

    public Instant getAppealedAt() { return appealedAt; }
    public void setAppealedAt(Instant appealedAt) { this.appealedAt = appealedAt; }

    public String getAppealOutcome() { return appealOutcome; }
    public void setAppealOutcome(String appealOutcome) { this.appealOutcome = appealOutcome; }

    public enum SubjectType { WORKER, SUPERVISOR, VEHICLE, VENDOR, BATCH }

    public enum Severity { LOW, MEDIUM, HIGH, CRITICAL }

    public enum Status { OPEN, INVESTIGATING, CONFIRMED, DISMISSED, APPEALED, CLOSED }

}
