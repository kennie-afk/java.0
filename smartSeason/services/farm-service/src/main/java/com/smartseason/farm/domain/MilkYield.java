package com.smartseason.farm.domain;

import com.smartseason.farm.platform.BaseEntity;
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
@Table(name = "milk_yields", indexes = {
        @Index(name = "ix_milk_yields_cow_id", columnList = "cow_id"),
        @Index(name = "ix_milk_yields_farm_id", columnList = "farm_id"),
        @Index(name = "ix_milk_yields_recorded_on", columnList = "recorded_on")
})
public class MilkYield extends BaseEntity {

    @Column(name = "cow_id", nullable = false)
    private UUID cowId;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "recorded_on", nullable = false)
    private LocalDate recordedOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "session", nullable = false)
    private Session session;

    @Column(name = "litres", nullable = false)
    private BigDecimal litres;

    @Column(name = "recorded_by")
    private UUID recordedBy;

    @Column(name = "notes")
    private String notes;

    public UUID getCowId() { return cowId; }
    public void setCowId(UUID cowId) { this.cowId = cowId; }

    public UUID getFarmId() { return farmId; }
    public void setFarmId(UUID farmId) { this.farmId = farmId; }

    public LocalDate getRecordedOn() { return recordedOn; }
    public void setRecordedOn(LocalDate recordedOn) { this.recordedOn = recordedOn; }

    public Session getSession() { return session; }
    public void setSession(Session session) { this.session = session; }

    public BigDecimal getLitres() { return litres; }
    public void setLitres(BigDecimal litres) { this.litres = litres; }

    public UUID getRecordedBy() { return recordedBy; }
    public void setRecordedBy(UUID recordedBy) { this.recordedBy = recordedBy; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public enum Session { MORNING, MIDDAY, EVENING }

}
