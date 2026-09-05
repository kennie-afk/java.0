package com.smartseason.automation.domain;

import com.smartseason.automation.platform.BaseEntity;
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
@Table(name = "actuator_commands", indexes = {
        @Index(name = "ix_actuator_commands_device_id", columnList = "device_id"),
        @Index(name = "ix_actuator_commands_rule_id", columnList = "rule_id"),
        @Index(name = "ix_actuator_commands_command_key", columnList = "command_key")
})
public class ActuatorCommand extends BaseEntity {

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "rule_id")
    private UUID ruleId;

    @Column(name = "command_key", nullable = false, unique = true)
    private String commandKey;

    @Column(name = "action", nullable = false)
    private String action;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private String payload;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "attempts", nullable = false)
    private Integer attempts;

    @Column(name = "failure_reason")
    private String failureReason;

    public UUID getDeviceId() { return deviceId; }
    public void setDeviceId(UUID deviceId) { this.deviceId = deviceId; }

    public UUID getRuleId() { return ruleId; }
    public void setRuleId(UUID ruleId) { this.ruleId = ruleId; }

    public String getCommandKey() { return commandKey; }
    public void setCommandKey(String commandKey) { this.commandKey = commandKey; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public Instant getIssuedAt() { return issuedAt; }
    public void setIssuedAt(Instant issuedAt) { this.issuedAt = issuedAt; }

    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(Instant acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public Integer getAttempts() { return attempts; }
    public void setAttempts(Integer attempts) { this.attempts = attempts; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public enum Status { PENDING, SENT, ACKED, COMPLETED, FAILED, TIMEOUT }

}
