package com.smartseason.task.domain;

import com.smartseason.task.platform.BaseEntity;
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
@Table(name = "task_evidence", indexes = {
        @Index(name = "ix_task_evidence_assignment_id", columnList = "assignment_id"),
        @Index(name = "ix_task_evidence_work_order_id", columnList = "work_order_id"),
        @Index(name = "ix_task_evidence_perceptual_hash", columnList = "perceptual_hash")
})
public class TaskEvidence extends BaseEntity {

    @Column(name = "assignment_id", nullable = false)
    private UUID assignmentId;

    @Column(name = "work_order_id")
    private UUID workOrderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "evidence_type", nullable = false)
    private EvidenceType evidenceType;

    @Column(name = "media_url")
    private String mediaUrl;

    @Column(name = "perceptual_hash")
    private String perceptualHash;

    @Column(name = "latitude")
    private BigDecimal latitude;

    @Column(name = "longitude")
    private BigDecimal longitude;

    @Column(name = "captured_at")
    private Instant capturedAt;

    @Column(name = "exif_timestamp")
    private Instant exifTimestamp;

    @Column(name = "mock_location", nullable = false)
    private Boolean mockLocation;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(name = "verdict", nullable = false)
    private Verdict verdict;

    public UUID getAssignmentId() { return assignmentId; }
    public void setAssignmentId(UUID assignmentId) { this.assignmentId = assignmentId; }

    public UUID getWorkOrderId() { return workOrderId; }
    public void setWorkOrderId(UUID workOrderId) { this.workOrderId = workOrderId; }

    public EvidenceType getEvidenceType() { return evidenceType; }
    public void setEvidenceType(EvidenceType evidenceType) { this.evidenceType = evidenceType; }

    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }

    public String getPerceptualHash() { return perceptualHash; }
    public void setPerceptualHash(String perceptualHash) { this.perceptualHash = perceptualHash; }

    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }

    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }

    public Instant getCapturedAt() { return capturedAt; }
    public void setCapturedAt(Instant capturedAt) { this.capturedAt = capturedAt; }

    public Instant getExifTimestamp() { return exifTimestamp; }
    public void setExifTimestamp(Instant exifTimestamp) { this.exifTimestamp = exifTimestamp; }

    public Boolean getMockLocation() { return mockLocation; }
    public void setMockLocation(Boolean mockLocation) { this.mockLocation = mockLocation; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Verdict getVerdict() { return verdict; }
    public void setVerdict(Verdict verdict) { this.verdict = verdict; }

    public enum EvidenceType { PHOTO, GPS, SIGNATURE, NOTE }

    public enum Verdict { PENDING, ACCEPTED, SUSPECT, REJECTED }

}
