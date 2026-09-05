package com.smartseason.logistics.domain;

import com.smartseason.logistics.platform.BaseEntity;
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
@Table(name = "proofs_of_delivery", indexes = {
        @Index(name = "ix_proofs_of_delivery_transport_job_id", columnList = "transport_job_id")
})
public class ProofOfDelivery extends BaseEntity {

    @Column(name = "transport_job_id", nullable = false, unique = true)
    private UUID transportJobId;

    @Column(name = "received_by", nullable = false)
    private String receivedBy;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "signature_url")
    private String signatureUrl;

    @Column(name = "photo_url")
    private String photoUrl;

    @Column(name = "latitude")
    private BigDecimal latitude;

    @Column(name = "longitude")
    private BigDecimal longitude;

    @Column(name = "delivered_weight_kg")
    private BigDecimal deliveredWeightKg;

    @Column(name = "variance_kg")
    private BigDecimal varianceKg;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "disputed", nullable = false)
    private Boolean disputed;

    public UUID getTransportJobId() { return transportJobId; }
    public void setTransportJobId(UUID transportJobId) { this.transportJobId = transportJobId; }

    public String getReceivedBy() { return receivedBy; }
    public void setReceivedBy(String receivedBy) { this.receivedBy = receivedBy; }

    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }

    public String getSignatureUrl() { return signatureUrl; }
    public void setSignatureUrl(String signatureUrl) { this.signatureUrl = signatureUrl; }

    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }

    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }

    public BigDecimal getDeliveredWeightKg() { return deliveredWeightKg; }
    public void setDeliveredWeightKg(BigDecimal deliveredWeightKg) { this.deliveredWeightKg = deliveredWeightKg; }

    public BigDecimal getVarianceKg() { return varianceKg; }
    public void setVarianceKg(BigDecimal varianceKg) { this.varianceKg = varianceKg; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public Boolean getDisputed() { return disputed; }
    public void setDisputed(Boolean disputed) { this.disputed = disputed; }

}
