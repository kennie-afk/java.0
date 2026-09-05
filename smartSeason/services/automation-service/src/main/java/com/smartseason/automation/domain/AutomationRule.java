package com.smartseason.automation.domain;

import com.smartseason.automation.platform.BaseEntity;
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
@Table(name = "automation_rules", indexes = {
        @Index(name = "ix_automation_rules_plot_id", columnList = "plot_id")
})
public class AutomationRule extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "plot_id")
    private UUID plotId;

    @Column(name = "trigger_metric")
    private String triggerMetric;

    @Enumerated(EnumType.STRING)
    @Column(name = "operator", nullable = false)
    private Operator operator;

    @Column(name = "threshold")
    private BigDecimal threshold;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private ActionType actionType;

    @Column(name = "action_target_device_id")
    private UUID actionTargetDeviceId;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "cooldown_seconds", nullable = false)
    private Integer cooldownSeconds;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "last_triggered_at")
    private Instant lastTriggeredAt;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public UUID getPlotId() { return plotId; }
    public void setPlotId(UUID plotId) { this.plotId = plotId; }

    public String getTriggerMetric() { return triggerMetric; }
    public void setTriggerMetric(String triggerMetric) { this.triggerMetric = triggerMetric; }

    public Operator getOperator() { return operator; }
    public void setOperator(Operator operator) { this.operator = operator; }

    public BigDecimal getThreshold() { return threshold; }
    public void setThreshold(BigDecimal threshold) { this.threshold = threshold; }

    public ActionType getActionType() { return actionType; }
    public void setActionType(ActionType actionType) { this.actionType = actionType; }

    public UUID getActionTargetDeviceId() { return actionTargetDeviceId; }
    public void setActionTargetDeviceId(UUID actionTargetDeviceId) { this.actionTargetDeviceId = actionTargetDeviceId; }

    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }

    public Integer getCooldownSeconds() { return cooldownSeconds; }
    public void setCooldownSeconds(Integer cooldownSeconds) { this.cooldownSeconds = cooldownSeconds; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Instant getLastTriggeredAt() { return lastTriggeredAt; }
    public void setLastTriggeredAt(Instant lastTriggeredAt) { this.lastTriggeredAt = lastTriggeredAt; }

    public enum Operator { LT, LTE, GT, GTE, EQ }

    public enum ActionType { IRRIGATE, VENTILATE, ALERT, DOSE }

}
