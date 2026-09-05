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
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "worker_risk_scores", indexes = {
        @Index(name = "ix_worker_risk_scores_worker_id", columnList = "worker_id"),
        @Index(name = "ix_worker_risk_scores_farm_id", columnList = "farm_id")
})
public class WorkerRiskScore extends BaseEntity {

    @Column(name = "worker_id", nullable = false, unique = true)
    private UUID workerId;

    @Column(name = "farm_id")
    private UUID farmId;

    @Column(name = "score", nullable = false)
    private Integer score;

    @Enumerated(EnumType.STRING)
    @Column(name = "band", nullable = false)
    private Band band;

    @Column(name = "open_cases", nullable = false)
    private Integer openCases;

    @Column(name = "last_signal_at")
    private Instant lastSignalAt;

    @Column(name = "decay_applied_at")
    private Instant decayAppliedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "components", columnDefinition = "jsonb")
    private String components;

    public UUID getWorkerId() { return workerId; }
    public void setWorkerId(UUID workerId) { this.workerId = workerId; }

    public UUID getFarmId() { return farmId; }
    public void setFarmId(UUID farmId) { this.farmId = farmId; }

    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }

    public Band getBand() { return band; }
    public void setBand(Band band) { this.band = band; }

    public Integer getOpenCases() { return openCases; }
    public void setOpenCases(Integer openCases) { this.openCases = openCases; }

    public Instant getLastSignalAt() { return lastSignalAt; }
    public void setLastSignalAt(Instant lastSignalAt) { this.lastSignalAt = lastSignalAt; }

    public Instant getDecayAppliedAt() { return decayAppliedAt; }
    public void setDecayAppliedAt(Instant decayAppliedAt) { this.decayAppliedAt = decayAppliedAt; }

    public String getComponents() { return components; }
    public void setComponents(String components) { this.components = components; }

    public enum Band { LOW, MEDIUM, HIGH, CRITICAL }

}
