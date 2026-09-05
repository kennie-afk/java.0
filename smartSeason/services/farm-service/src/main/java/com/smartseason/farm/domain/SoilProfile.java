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
@Table(name = "soil_profiles", indexes = {
        @Index(name = "ix_soil_profiles_plot_id", columnList = "plot_id")
})
public class SoilProfile extends BaseEntity {

    @Column(name = "plot_id", nullable = false)
    private UUID plotId;

    @Column(name = "sampled_at", nullable = false)
    private LocalDate sampledAt;

    @Column(name = "ph")
    private BigDecimal ph;

    @Column(name = "nitrogen_ppm")
    private BigDecimal nitrogenPpm;

    @Column(name = "phosphorus_ppm")
    private BigDecimal phosphorusPpm;

    @Column(name = "potassium_ppm")
    private BigDecimal potassiumPpm;

    @Column(name = "organic_carbon_pct")
    private BigDecimal organicCarbonPct;

    @Column(name = "texture")
    private String texture;

    @Column(name = "lab_name")
    private String labName;

    @Column(name = "report_url")
    private String reportUrl;

    public UUID getPlotId() { return plotId; }
    public void setPlotId(UUID plotId) { this.plotId = plotId; }

    public LocalDate getSampledAt() { return sampledAt; }
    public void setSampledAt(LocalDate sampledAt) { this.sampledAt = sampledAt; }

    public BigDecimal getPh() { return ph; }
    public void setPh(BigDecimal ph) { this.ph = ph; }

    public BigDecimal getNitrogenPpm() { return nitrogenPpm; }
    public void setNitrogenPpm(BigDecimal nitrogenPpm) { this.nitrogenPpm = nitrogenPpm; }

    public BigDecimal getPhosphorusPpm() { return phosphorusPpm; }
    public void setPhosphorusPpm(BigDecimal phosphorusPpm) { this.phosphorusPpm = phosphorusPpm; }

    public BigDecimal getPotassiumPpm() { return potassiumPpm; }
    public void setPotassiumPpm(BigDecimal potassiumPpm) { this.potassiumPpm = potassiumPpm; }

    public BigDecimal getOrganicCarbonPct() { return organicCarbonPct; }
    public void setOrganicCarbonPct(BigDecimal organicCarbonPct) { this.organicCarbonPct = organicCarbonPct; }

    public String getTexture() { return texture; }
    public void setTexture(String texture) { this.texture = texture; }

    public String getLabName() { return labName; }
    public void setLabName(String labName) { this.labName = labName; }

    public String getReportUrl() { return reportUrl; }
    public void setReportUrl(String reportUrl) { this.reportUrl = reportUrl; }

}
