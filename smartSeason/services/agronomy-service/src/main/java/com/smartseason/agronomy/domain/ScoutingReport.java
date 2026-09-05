package com.smartseason.agronomy.domain;

import com.smartseason.agronomy.platform.BaseEntity;
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
@Table(name = "scouting_reports", indexes = {
        @Index(name = "ix_scouting_reports_plot_id", columnList = "plot_id"),
        @Index(name = "ix_scouting_reports_season_id", columnList = "season_id")
})
public class ScoutingReport extends BaseEntity {

    @Column(name = "plot_id", nullable = false)
    private UUID plotId;

    @Column(name = "season_id")
    private UUID seasonId;

    @Column(name = "scouted_by", nullable = false)
    private UUID scoutedBy;

    @Column(name = "scouted_at", nullable = false)
    private Instant scoutedAt;

    @Column(name = "pest_disease_code")
    private String pestDiseaseCode;

    @Column(name = "incidence_pct")
    private BigDecimal incidencePct;

    @Column(name = "severity_score")
    private Integer severityScore;

    @Column(name = "latitude")
    private BigDecimal latitude;

    @Column(name = "longitude")
    private BigDecimal longitude;

    @Column(name = "photo_url")
    private String photoUrl;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    public UUID getPlotId() { return plotId; }
    public void setPlotId(UUID plotId) { this.plotId = plotId; }

    public UUID getSeasonId() { return seasonId; }
    public void setSeasonId(UUID seasonId) { this.seasonId = seasonId; }

    public UUID getScoutedBy() { return scoutedBy; }
    public void setScoutedBy(UUID scoutedBy) { this.scoutedBy = scoutedBy; }

    public Instant getScoutedAt() { return scoutedAt; }
    public void setScoutedAt(Instant scoutedAt) { this.scoutedAt = scoutedAt; }

    public String getPestDiseaseCode() { return pestDiseaseCode; }
    public void setPestDiseaseCode(String pestDiseaseCode) { this.pestDiseaseCode = pestDiseaseCode; }

    public BigDecimal getIncidencePct() { return incidencePct; }
    public void setIncidencePct(BigDecimal incidencePct) { this.incidencePct = incidencePct; }

    public Integer getSeverityScore() { return severityScore; }
    public void setSeverityScore(Integer severityScore) { this.severityScore = severityScore; }

    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }

    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }

    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public enum Status { OPEN, ACTIONED, CLOSED }

}
