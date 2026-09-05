package com.smartseason.fraud.domain;

import com.smartseason.fraud.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "fraud_rules", indexes = {
        @Index(name = "ix_fraud_rules_code", columnList = "code")
})
public class FraudRule extends BaseEntity {

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "typology", nullable = false)
    private Typology typology;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "expression", nullable = false, columnDefinition = "TEXT")
    private String expression;

    @Column(name = "threshold")
    private BigDecimal threshold;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private Severity severity;

    @Column(name = "weight", nullable = false)
    private Integer weight;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "auto_hold_payout", nullable = false)
    private Boolean autoHoldPayout;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public Typology getTypology() { return typology; }
    public void setTypology(Typology typology) { this.typology = typology; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getExpression() { return expression; }
    public void setExpression(String expression) { this.expression = expression; }

    public BigDecimal getThreshold() { return threshold; }
    public void setThreshold(BigDecimal threshold) { this.threshold = threshold; }

    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }

    public Integer getWeight() { return weight; }
    public void setWeight(Integer weight) { this.weight = weight; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public Boolean getAutoHoldPayout() { return autoHoldPayout; }
    public void setAutoHoldPayout(Boolean autoHoldPayout) { this.autoHoldPayout = autoHoldPayout; }

    public enum Typology { GHOST_WORKER, PROXY_CLOCK_IN, PIECE_RATE_INFLATION, INPUT_DIVERSION, HARVEST_SKIMMING, VEHICLE_FUEL, COLLUSION, EVIDENCE_FRAUD, HOURS_INFLATION, PROCUREMENT_KICKBACK }

    public enum Severity { LOW, MEDIUM, HIGH, CRITICAL }

}
