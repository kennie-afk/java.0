package com.smartseason.fraud.domain;

import com.smartseason.fraud.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "fraud_signals", indexes = {
        @Index(name = "ix_fraud_signals_subject_id", columnList = "subject_id"),
        @Index(name = "ix_fraud_signals_rule_code", columnList = "rule_code"),
        @Index(name = "ix_fraud_signals_case_id", columnList = "case_id")
})
public class FraudSignal extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "subject_type", nullable = false)
    private SubjectType subjectType;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "rule_code", nullable = false)
    private String ruleCode;

    @Column(name = "typology", nullable = false)
    private String typology;

    @Column(name = "score", nullable = false)
    private BigDecimal score;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    @Column(name = "source_event")
    private String sourceEvent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", columnDefinition = "jsonb")
    private String details;

    @Column(name = "case_id")
    private UUID caseId;

    public SubjectType getSubjectType() { return subjectType; }
    public void setSubjectType(SubjectType subjectType) { this.subjectType = subjectType; }

    public UUID getSubjectId() { return subjectId; }
    public void setSubjectId(UUID subjectId) { this.subjectId = subjectId; }

    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }

    public String getTypology() { return typology; }
    public void setTypology(String typology) { this.typology = typology; }

    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }

    public Instant getDetectedAt() { return detectedAt; }
    public void setDetectedAt(Instant detectedAt) { this.detectedAt = detectedAt; }

    public String getSourceEvent() { return sourceEvent; }
    public void setSourceEvent(String sourceEvent) { this.sourceEvent = sourceEvent; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public UUID getCaseId() { return caseId; }
    public void setCaseId(UUID caseId) { this.caseId = caseId; }

    public enum SubjectType { WORKER, SUPERVISOR, VEHICLE, VENDOR, BATCH }

}
