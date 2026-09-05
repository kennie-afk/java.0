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
@Table(name = "fraud_evidence", indexes = {
        @Index(name = "ix_fraud_evidence_case_id", columnList = "case_id")
})
public class FraudEvidence extends BaseEntity {

    @Column(name = "case_id", nullable = false)
    private UUID caseId;

    @Column(name = "label", nullable = false)
    private String label;

    @Column(name = "evidence_type", nullable = false)
    private String evidenceType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private String payload;

    @Column(name = "weight")
    private BigDecimal weight;

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;

    public UUID getCaseId() { return caseId; }
    public void setCaseId(UUID caseId) { this.caseId = caseId; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getEvidenceType() { return evidenceType; }
    public void setEvidenceType(String evidenceType) { this.evidenceType = evidenceType; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public BigDecimal getWeight() { return weight; }
    public void setWeight(BigDecimal weight) { this.weight = weight; }

    public Instant getCollectedAt() { return collectedAt; }
    public void setCollectedAt(Instant collectedAt) { this.collectedAt = collectedAt; }

}
