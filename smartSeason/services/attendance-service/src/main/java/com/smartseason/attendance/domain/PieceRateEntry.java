package com.smartseason.attendance.domain;

import com.smartseason.attendance.platform.BaseEntity;
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
@Table(name = "piece_rate_entries", indexes = {
        @Index(name = "ix_piece_rate_entries_worker_id", columnList = "worker_id"),
        @Index(name = "ix_piece_rate_entries_shift_id", columnList = "shift_id"),
        @Index(name = "ix_piece_rate_entries_farm_id", columnList = "farm_id"),
        @Index(name = "ix_piece_rate_entries_plot_id", columnList = "plot_id")
})
public class PieceRateEntry extends BaseEntity {

    @Column(name = "worker_id", nullable = false)
    private UUID workerId;

    @Column(name = "shift_id")
    private UUID shiftId;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "plot_id")
    private UUID plotId;

    @Column(name = "task_code", nullable = false)
    private String taskCode;

    @Column(name = "quantity", nullable = false)
    private BigDecimal quantity;

    @Column(name = "unit", nullable = false)
    private String unit;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "recorded_by")
    private UUID recordedBy;

    @Column(name = "weigh_station_id")
    private String weighStationId;

    @Column(name = "verified_by")
    private UUID verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    public UUID getWorkerId() { return workerId; }
    public void setWorkerId(UUID workerId) { this.workerId = workerId; }

    public UUID getShiftId() { return shiftId; }
    public void setShiftId(UUID shiftId) { this.shiftId = shiftId; }

    public UUID getFarmId() { return farmId; }
    public void setFarmId(UUID farmId) { this.farmId = farmId; }

    public UUID getPlotId() { return plotId; }
    public void setPlotId(UUID plotId) { this.plotId = plotId; }

    public String getTaskCode() { return taskCode; }
    public void setTaskCode(String taskCode) { this.taskCode = taskCode; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public Instant getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Instant recordedAt) { this.recordedAt = recordedAt; }

    public UUID getRecordedBy() { return recordedBy; }
    public void setRecordedBy(UUID recordedBy) { this.recordedBy = recordedBy; }

    public String getWeighStationId() { return weighStationId; }
    public void setWeighStationId(String weighStationId) { this.weighStationId = weighStationId; }

    public UUID getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(UUID verifiedBy) { this.verifiedBy = verifiedBy; }

    public Instant getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(Instant verifiedAt) { this.verifiedAt = verifiedAt; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public enum Status { RECORDED, VERIFIED, DISPUTED, VOID }

}
