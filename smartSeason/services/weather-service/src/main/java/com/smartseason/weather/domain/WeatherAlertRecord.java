package com.smartseason.weather.domain;

import com.smartseason.weather.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "weather_alerts", indexes = {
        @Index(name = "ix_weather_alerts_geo_cell", columnList = "geo_cell")
})
public class WeatherAlertRecord extends BaseEntity {

    @Column(name = "geo_cell", nullable = false)
    private String geoCell;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false)
    private AlertType alertType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private Severity severity;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "headline", nullable = false)
    private String headline;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "source")
    private String source;

    public String getGeoCell() { return geoCell; }
    public void setGeoCell(String geoCell) { this.geoCell = geoCell; }

    public AlertType getAlertType() { return alertType; }
    public void setAlertType(AlertType alertType) { this.alertType = alertType; }

    public Severity getSeverity() { return severity; }
    public void setSeverity(Severity severity) { this.severity = severity; }

    public Instant getStartsAt() { return startsAt; }
    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }

    public Instant getEndsAt() { return endsAt; }
    public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; }

    public String getHeadline() { return headline; }
    public void setHeadline(String headline) { this.headline = headline; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public enum AlertType { DROUGHT, FLOOD, FROST, HAIL, HEATWAVE, STORM }

    public enum Severity { LOW, MEDIUM, HIGH, EXTREME }

}
