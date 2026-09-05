package com.smartseason.media.domain;

import com.smartseason.media.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "upload_tickets", indexes = {
        @Index(name = "ix_upload_tickets_storage_key", columnList = "storage_key")
})
public class UploadTicket extends BaseEntity {

    @Column(name = "storage_key", nullable = false, unique = true)
    private String storageKey;

    @Column(name = "upload_url", nullable = false, columnDefinition = "TEXT")
    private String uploadUrl;

    @Column(name = "method", nullable = false)
    private String method;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "max_size_bytes")
    private Long maxSizeBytes;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    public String getStorageKey() { return storageKey; }
    public void setStorageKey(String storageKey) { this.storageKey = storageKey; }

    public String getUploadUrl() { return uploadUrl; }
    public void setUploadUrl(String uploadUrl) { this.uploadUrl = uploadUrl; }

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }

    public UUID getRequestedBy() { return requestedBy; }
    public void setRequestedBy(UUID requestedBy) { this.requestedBy = requestedBy; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public Long getMaxSizeBytes() { return maxSizeBytes; }
    public void setMaxSizeBytes(Long maxSizeBytes) { this.maxSizeBytes = maxSizeBytes; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public Instant getConsumedAt() { return consumedAt; }
    public void setConsumedAt(Instant consumedAt) { this.consumedAt = consumedAt; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public enum Status { ISSUED, CONSUMED, EXPIRED }

}
