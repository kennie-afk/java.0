package com.smartseason.automation.domain;

import com.smartseason.automation.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "safety_interlocks", indexes = {
        @Index(name = "ix_safety_interlocks_device_id", columnList = "device_id")
})
public class SafetyInterlock extends BaseEntity {

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "interlock_type", nullable = false)
    private InterlockType interlockType;

    @Column(name = "max_runtime_seconds")
    private Integer maxRuntimeSeconds;

    @Column(name = "conflicting_device_id")
    private UUID conflictingDeviceId;

    @Column(name = "engaged", nullable = false)
    private Boolean engaged;

    @Column(name = "engaged_at")
    private Instant engagedAt;

    @Column(name = "reason")
    private String reason;

    public UUID getDeviceId() { return deviceId; }
    public void setDeviceId(UUID deviceId) { this.deviceId = deviceId; }

    public InterlockType getInterlockType() { return interlockType; }
    public void setInterlockType(InterlockType interlockType) { this.interlockType = interlockType; }

    public Integer getMaxRuntimeSeconds() { return maxRuntimeSeconds; }
    public void setMaxRuntimeSeconds(Integer maxRuntimeSeconds) { this.maxRuntimeSeconds = maxRuntimeSeconds; }

    public UUID getConflictingDeviceId() { return conflictingDeviceId; }
    public void setConflictingDeviceId(UUID conflictingDeviceId) { this.conflictingDeviceId = conflictingDeviceId; }

    public Boolean getEngaged() { return engaged; }
    public void setEngaged(Boolean engaged) { this.engaged = engaged; }

    public Instant getEngagedAt() { return engagedAt; }
    public void setEngagedAt(Instant engagedAt) { this.engagedAt = engagedAt; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public enum InterlockType { MAX_RUNTIME, MUTUAL_EXCLUSION, MANUAL_OVERRIDE }

}
