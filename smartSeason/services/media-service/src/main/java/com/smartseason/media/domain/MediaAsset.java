package com.smartseason.media.domain;

import com.smartseason.media.platform.BaseEntity;
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
@Table(name = "media_assets", indexes = {
        @Index(name = "ix_media_assets_storage_key", columnList = "storage_key"),
        @Index(name = "ix_media_assets_checksum", columnList = "checksum"),
        @Index(name = "ix_media_assets_owner_user_id", columnList = "owner_user_id"),
        @Index(name = "ix_media_assets_context", columnList = "context"),
        @Index(name = "ix_media_assets_context_ref", columnList = "context_ref"),
        @Index(name = "ix_media_assets_perceptual_hash", columnList = "perceptual_hash")
})
public class MediaAsset extends BaseEntity {

    @Column(name = "storage_key", nullable = false, unique = true)
    private String storageKey;

    @Column(name = "original_filename")
    private String originalFilename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "checksum")
    private String checksum;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(name = "context")
    private String context;

    @Column(name = "context_ref")
    private String contextRef;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "perceptual_hash")
    private String perceptualHash;

    @Column(name = "exif_timestamp")
    private Instant exifTimestamp;

    @Column(name = "exif_latitude")
    private BigDecimal exifLatitude;

    @Column(name = "exif_longitude")
    private BigDecimal exifLongitude;

    @Column(name = "public_url")
    private String publicUrl;

    @Column(name = "virus_scanned", nullable = false)
    private Boolean virusScanned;

    @Column(name = "virus_clean", nullable = false)
    private Boolean virusClean;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }

    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }

    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }

    public UUID getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(UUID ownerUserId) { this.ownerUserId = ownerUserId; }

    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }

    public String getContextRef() { return contextRef; }
    public void setContextRef(String contextRef) { this.contextRef = contextRef; }

    public Integer getWidth() { return width; }
    public void setWidth(Integer width) { this.width = width; }

    public Integer getHeight() { return height; }
    public void setHeight(Integer height) { this.height = height; }

    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }

    public String getPerceptualHash() { return perceptualHash; }
    public void setPerceptualHash(String perceptualHash) { this.perceptualHash = perceptualHash; }

    public Instant getExifTimestamp() { return exifTimestamp; }
    public void setExifTimestamp(Instant exifTimestamp) { this.exifTimestamp = exifTimestamp; }

    public BigDecimal getExifLatitude() { return exifLatitude; }
    public void setExifLatitude(BigDecimal exifLatitude) { this.exifLatitude = exifLatitude; }

    public BigDecimal getExifLongitude() { return exifLongitude; }
    public void setExifLongitude(BigDecimal exifLongitude) { this.exifLongitude = exifLongitude; }

    public String getPublicUrl() { return publicUrl; }
    public void setPublicUrl(String publicUrl) { this.publicUrl = publicUrl; }

    public Boolean getVirusScanned() { return virusScanned; }
    public void setVirusScanned(Boolean virusScanned) { this.virusScanned = virusScanned; }

    public Boolean getVirusClean() { return virusClean; }
    public void setVirusClean(Boolean virusClean) { this.virusClean = virusClean; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public enum Status { PENDING, UPLOADED, PROCESSING, READY, QUARANTINED, DELETED }

}
