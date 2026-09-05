package com.smartseason.season.domain;

import com.smartseason.season.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

@Entity
@Table(name = "stage_templates", indexes = {
        @Index(name = "ix_stage_templates_crop_code", columnList = "crop_code")
})
public class StageTemplate extends BaseEntity {

    @Column(name = "crop_code", nullable = false)
    private String cropCode;

    @Column(name = "stage_name", nullable = false)
    private String stageName;

    @Column(name = "sequence", nullable = false)
    private Integer sequence;

    @Column(name = "duration_days", nullable = false)
    private Integer durationDays;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "key_activities", columnDefinition = "TEXT")
    private String keyActivities;

    public String getCropCode() { return cropCode; }
    public void setCropCode(String cropCode) { this.cropCode = cropCode; }

    public String getStageName() { return stageName; }
    public void setStageName(String stageName) { this.stageName = stageName; }

    public Integer getSequence() { return sequence; }
    public void setSequence(Integer sequence) { this.sequence = sequence; }

    public Integer getDurationDays() { return durationDays; }
    public void setDurationDays(Integer durationDays) { this.durationDays = durationDays; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getKeyActivities() { return keyActivities; }
    public void setKeyActivities(String keyActivities) { this.keyActivities = keyActivities; }

}
