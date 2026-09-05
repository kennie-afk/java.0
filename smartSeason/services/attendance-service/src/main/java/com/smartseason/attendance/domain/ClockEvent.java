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
@Table(name = "clock_events", indexes = {
        @Index(name = "ix_clock_events_worker_id", columnList = "worker_id"),
        @Index(name = "ix_clock_events_farm_id", columnList = "farm_id"),
        @Index(name = "ix_clock_events_shift_id", columnList = "shift_id"),
        @Index(name = "ix_clock_events_client_event_id", columnList = "client_event_id")
})
public class ClockEvent extends BaseEntity {

    @Column(name = "worker_id", nullable = false)
    private UUID workerId;

    @Column(name = "farm_id", nullable = false)
    private UUID farmId;

    @Column(name = "shift_id")
    private UUID shiftId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "latitude")
    private BigDecimal latitude;

    @Column(name = "longitude")
    private BigDecimal longitude;

    @Column(name = "accuracy_m")
    private BigDecimal accuracyM;

    @Column(name = "geofence_id")
    private UUID geofenceId;

    @Column(name = "inside_geofence", nullable = false)
    private Boolean insideGeofence;

    @Column(name = "biometric_score")
    private BigDecimal biometricScore;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "mock_location", nullable = false)
    private Boolean mockLocation;

    @Column(name = "offline_synced", nullable = false)
    private Boolean offlineSynced;

    @Column(name = "client_event_id", unique = true)
    private String clientEventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "verdict", nullable = false)
    private Verdict verdict;

    @Column(name = "flag_reason")
    private String flagReason;

    public UUID getWorkerId() { return workerId; }
    public void setWorkerId(UUID workerId) { this.workerId = workerId; }

    public UUID getFarmId() { return farmId; }
    public void setFarmId(UUID farmId) { this.farmId = farmId; }

    public UUID getShiftId() { return shiftId; }
    public void setShiftId(UUID shiftId) { this.shiftId = shiftId; }

    public EventType getEventType() { return eventType; }
    public void setEventType(EventType eventType) { this.eventType = eventType; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }

    public Instant getRecordedAt() { return recordedAt; }
    public void setRecordedAt(Instant recordedAt) { this.recordedAt = recordedAt; }

    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }

    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }

    public BigDecimal getAccuracyM() { return accuracyM; }
    public void setAccuracyM(BigDecimal accuracyM) { this.accuracyM = accuracyM; }

    public UUID getGeofenceId() { return geofenceId; }
    public void setGeofenceId(UUID geofenceId) { this.geofenceId = geofenceId; }

    public Boolean getInsideGeofence() { return insideGeofence; }
    public void setInsideGeofence(Boolean insideGeofence) { this.insideGeofence = insideGeofence; }

    public BigDecimal getBiometricScore() { return biometricScore; }
    public void setBiometricScore(BigDecimal biometricScore) { this.biometricScore = biometricScore; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public Boolean getMockLocation() { return mockLocation; }
    public void setMockLocation(Boolean mockLocation) { this.mockLocation = mockLocation; }

    public Boolean getOfflineSynced() { return offlineSynced; }
    public void setOfflineSynced(Boolean offlineSynced) { this.offlineSynced = offlineSynced; }

    public String getClientEventId() { return clientEventId; }
    public void setClientEventId(String clientEventId) { this.clientEventId = clientEventId; }

    public Verdict getVerdict() { return verdict; }
    public void setVerdict(Verdict verdict) { this.verdict = verdict; }

    public String getFlagReason() { return flagReason; }
    public void setFlagReason(String flagReason) { this.flagReason = flagReason; }

    public enum EventType { CLOCK_IN, CLOCK_OUT, BREAK_START, BREAK_END }

    public enum Verdict { ACCEPTED, FLAGGED, REJECTED }

}
