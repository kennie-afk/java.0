package com.smartseason.agronomy.casework;

import com.smartseason.agronomy.domain.Advisory;
import com.smartseason.agronomy.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A question from the field that needs a person: the farmer's words, what the AI said, and who
 * is answering. The unread flags are the in-app notification: each side sees a badge until they
 * open the case.
 */
@Entity
@Table(name = "advisory_cases")
public class AdvisoryCase extends BaseEntity {

    public enum Status { OPEN, ASSIGNED, ANSWERED, CLOSED }

    public enum Reason { REQUESTED, LOW_CONFIDENCE, HIGH_SEVERITY }

    @Column(name = "plot_id")
    private UUID plotId;
    @Column(name = "season_id")
    private UUID seasonId;
    @Column(name = "crop_code")
    private String cropCode;
    @Column(name = "county")
    private String county;
    @Column(name = "raised_by", nullable = false)
    private UUID raisedBy;
    @Column(name = "raised_by_name")
    private String raisedByName;
    @Column(name = "raised_by_email")
    private String raisedByEmail;
    @Column(name = "question", nullable = false, columnDefinition = "TEXT")
    private String question;
    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false)
    private Reason reason;
    @Column(name = "ai_source")
    private String aiSource;
    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;
    @Column(name = "ai_confidence")
    private Double aiConfidence;
    @Enumerated(EnumType.STRING)
    @Column(name = "ai_severity")
    private Advisory.Severity aiSeverity;
    @Column(name = "priority", nullable = false)
    private int priority;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;
    @Column(name = "assigned_to")
    private UUID assignedTo;
    @Column(name = "assigned_at")
    private Instant assignedAt;
    @Column(name = "due_at", nullable = false)
    private Instant dueAt;
    @Column(name = "answered_at")
    private Instant answeredAt;
    @Column(name = "closed_at")
    private Instant closedAt;
    @Column(name = "advisory_id")
    private UUID advisoryId;
    @Column(name = "photo_count", nullable = false)
    private int photoCount;
    @Column(name = "farmer_unread", nullable = false)
    private boolean farmerUnread;
    @Column(name = "agronomist_unread", nullable = false)
    private boolean agronomistUnread;

    public UUID getPlotId() { return plotId; }
    public void setPlotId(UUID v) { this.plotId = v; }
    public UUID getSeasonId() { return seasonId; }
    public void setSeasonId(UUID v) { this.seasonId = v; }
    public String getCropCode() { return cropCode; }
    public void setCropCode(String v) { this.cropCode = v; }
    public String getCounty() { return county; }
    public void setCounty(String v) { this.county = v; }
    public UUID getRaisedBy() { return raisedBy; }
    public void setRaisedBy(UUID v) { this.raisedBy = v; }
    public String getRaisedByName() { return raisedByName; }
    public void setRaisedByName(String v) { this.raisedByName = v; }
    public String getRaisedByEmail() { return raisedByEmail; }
    public void setRaisedByEmail(String v) { this.raisedByEmail = v; }
    public String getQuestion() { return question; }
    public void setQuestion(String v) { this.question = v; }
    public Reason getReason() { return reason; }
    public void setReason(Reason v) { this.reason = v; }
    public String getAiSource() { return aiSource; }
    public void setAiSource(String v) { this.aiSource = v; }
    public String getAiSummary() { return aiSummary; }
    public void setAiSummary(String v) { this.aiSummary = v; }
    public Double getAiConfidence() { return aiConfidence; }
    public void setAiConfidence(Double v) { this.aiConfidence = v; }
    public Advisory.Severity getAiSeverity() { return aiSeverity; }
    public void setAiSeverity(Advisory.Severity v) { this.aiSeverity = v; }
    public int getPriority() { return priority; }
    public void setPriority(int v) { this.priority = v; }
    public Status getStatus() { return status; }
    public void setStatus(Status v) { this.status = v; }
    public UUID getAssignedTo() { return assignedTo; }
    public void setAssignedTo(UUID v) { this.assignedTo = v; }
    public Instant getAssignedAt() { return assignedAt; }
    public void setAssignedAt(Instant v) { this.assignedAt = v; }
    public Instant getDueAt() { return dueAt; }
    public void setDueAt(Instant v) { this.dueAt = v; }
    public Instant getAnsweredAt() { return answeredAt; }
    public void setAnsweredAt(Instant v) { this.answeredAt = v; }
    public Instant getClosedAt() { return closedAt; }
    public void setClosedAt(Instant v) { this.closedAt = v; }
    public UUID getAdvisoryId() { return advisoryId; }
    public void setAdvisoryId(UUID v) { this.advisoryId = v; }
    public int getPhotoCount() { return photoCount; }
    public void setPhotoCount(int v) { this.photoCount = v; }
    public boolean isFarmerUnread() { return farmerUnread; }
    public void setFarmerUnread(boolean v) { this.farmerUnread = v; }
    public boolean isAgronomistUnread() { return agronomistUnread; }
    public void setAgronomistUnread(boolean v) { this.agronomistUnread = v; }
}
