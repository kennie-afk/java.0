package com.smartseason.agronomy.domain;

import com.smartseason.agronomy.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "crop_playbooks", indexes = {
        @Index(name = "ix_crop_playbooks_crop_code", columnList = "crop_code")
})
public class CropPlaybook extends BaseEntity {

    @Column(name = "crop_code", nullable = false)
    private String cropCode;

    @Column(name = "stage_name", nullable = false)
    private String stageName;

    @Column(name = "guidance", nullable = false, columnDefinition = "TEXT")
    private String guidance;

    @Column(name = "input_recommendations", columnDefinition = "TEXT")
    private String inputRecommendations;

    @Column(name = "risk_factors", columnDefinition = "TEXT")
    private String riskFactors;

    @Column(name = "revision", nullable = false)
    private Integer revision;

    public String getCropCode() { return cropCode; }
    public void setCropCode(String cropCode) { this.cropCode = cropCode; }

    public String getStageName() { return stageName; }
    public void setStageName(String stageName) { this.stageName = stageName; }

    public String getGuidance() { return guidance; }
    public void setGuidance(String guidance) { this.guidance = guidance; }

    public String getInputRecommendations() { return inputRecommendations; }
    public void setInputRecommendations(String inputRecommendations) { this.inputRecommendations = inputRecommendations; }

    public String getRiskFactors() { return riskFactors; }
    public void setRiskFactors(String riskFactors) { this.riskFactors = riskFactors; }

    public Integer getRevision() { return revision; }
    public void setRevision(Integer revision) { this.revision = revision; }

}
