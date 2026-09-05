package com.smartseason.workforce.domain;

import com.smartseason.workforce.platform.BaseEntity;
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
@Table(name = "worker_contracts", indexes = {
        @Index(name = "ix_worker_contracts_worker_id", columnList = "worker_id"),
        @Index(name = "ix_worker_contracts_farm_id", columnList = "farm_id")
})
public class WorkerContract extends BaseEntity {

    @Column(name = "worker_id", nullable = false)
    private UUID workerId;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Enumerated(EnumType.STRING)
    @Column(name = "contract_type", nullable = false)
    private ContractType contractType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "daily_rate")
    private BigDecimal dailyRate;

    @Column(name = "piece_rate")
    private BigDecimal pieceRate;

    @Column(name = "piece_unit")
    private String pieceUnit;

    @Column(name = "supervisor_id")
    private UUID supervisorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "terms", columnDefinition = "TEXT")
    private String terms;

    public UUID getWorkerId() { return workerId; }
    public void setWorkerId(UUID workerId) { this.workerId = workerId; }

    public UUID getFarmId() { return farmId; }
    public void setFarmId(UUID farmId) { this.farmId = farmId; }

    public ContractType getContractType() { return contractType; }
    public void setContractType(ContractType contractType) { this.contractType = contractType; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }

    public BigDecimal getDailyRate() { return dailyRate; }
    public void setDailyRate(BigDecimal dailyRate) { this.dailyRate = dailyRate; }

    public BigDecimal getPieceRate() { return pieceRate; }
    public void setPieceRate(BigDecimal pieceRate) { this.pieceRate = pieceRate; }

    public String getPieceUnit() { return pieceUnit; }
    public void setPieceUnit(String pieceUnit) { this.pieceUnit = pieceUnit; }

    public UUID getSupervisorId() { return supervisorId; }
    public void setSupervisorId(UUID supervisorId) { this.supervisorId = supervisorId; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getTerms() { return terms; }
    public void setTerms(String terms) { this.terms = terms; }

    public enum ContractType { CASUAL, SEASONAL, PERMANENT, PIECE_RATE }

    public enum Status { DRAFT, ACTIVE, ENDED, TERMINATED }

}
