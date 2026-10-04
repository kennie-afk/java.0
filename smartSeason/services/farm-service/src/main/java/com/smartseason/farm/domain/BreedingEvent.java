package com.smartseason.farm.domain;

import com.smartseason.farm.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "breeding_events", indexes = {
        @Index(name = "ix_breeding_events_cow_id", columnList = "cow_id"),
        @Index(name = "ix_breeding_events_farm_id", columnList = "farm_id"),
        @Index(name = "ix_breeding_events_event_date", columnList = "event_date")
})
public class BreedingEvent extends BaseEntity {

    @Column(name = "cow_id", nullable = false)
    private UUID cowId;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "method")
    private Method method;

    @Column(name = "sire_ref")
    private String sireRef;

    @Column(name = "outcome")
    private String outcome;

    @Column(name = "expected_calving_on")
    private LocalDate expectedCalvingOn;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    public UUID getCowId() { return cowId; }
    public void setCowId(UUID cowId) { this.cowId = cowId; }

    public UUID getFarmId() { return farmId; }
    public void setFarmId(UUID farmId) { this.farmId = farmId; }

    public LocalDate getEventDate() { return eventDate; }
    public void setEventDate(LocalDate eventDate) { this.eventDate = eventDate; }

    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }

    public Method getMethod() { return method; }
    public void setMethod(Method method) { this.method = method; }

    public String getSireRef() { return sireRef; }
    public void setSireRef(String sireRef) { this.sireRef = sireRef; }

    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }

    public LocalDate getExpectedCalvingOn() { return expectedCalvingOn; }
    public void setExpectedCalvingOn(LocalDate expectedCalvingOn) { this.expectedCalvingOn = expectedCalvingOn; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public enum EventType { HEAT, SERVICE, PREGNANCY_CHECK, CALVING, DRY_OFF }

    public enum Method { AI, NATURAL }

}
