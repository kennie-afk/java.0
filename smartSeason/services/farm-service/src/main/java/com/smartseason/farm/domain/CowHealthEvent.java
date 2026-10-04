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
@Table(name = "cow_health_events", indexes = {
        @Index(name = "ix_cow_health_events_cow_id", columnList = "cow_id"),
        @Index(name = "ix_cow_health_events_farm_id", columnList = "farm_id"),
        @Index(name = "ix_cow_health_events_event_date", columnList = "event_date"),
        @Index(name = "ix_cow_health_events_withdrawal_ends_on", columnList = "withdrawal_ends_on")
})
public class CowHealthEvent extends BaseEntity {

    @Column(name = "cow_id", nullable = false)
    private UUID cowId;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "medicine")
    private String medicine;

    @Column(name = "withdrawal_ends_on")
    private LocalDate withdrawalEndsOn;

    @Column(name = "vet_name")
    private String vetName;

    @Column(name = "cost_amount")
    private BigDecimal costAmount;

    public UUID getCowId() { return cowId; }
    public void setCowId(UUID cowId) { this.cowId = cowId; }

    public UUID getFarmId() { return farmId; }
    public void setFarmId(UUID farmId) { this.farmId = farmId; }

    public LocalDate getEventDate() { return eventDate; }
    public void setEventDate(LocalDate eventDate) { this.eventDate = eventDate; }

    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getMedicine() { return medicine; }
    public void setMedicine(String medicine) { this.medicine = medicine; }

    public LocalDate getWithdrawalEndsOn() { return withdrawalEndsOn; }
    public void setWithdrawalEndsOn(LocalDate withdrawalEndsOn) { this.withdrawalEndsOn = withdrawalEndsOn; }

    public String getVetName() { return vetName; }
    public void setVetName(String vetName) { this.vetName = vetName; }

    public BigDecimal getCostAmount() { return costAmount; }
    public void setCostAmount(BigDecimal costAmount) { this.costAmount = costAmount; }

    public enum EventType { VACCINATION, TREATMENT, DEWORMING, MASTITIS, VET_VISIT, OTHER }

}
