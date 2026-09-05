package com.smartseason.notification.domain;

import com.smartseason.notification.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_receipts", indexes = {
        @Index(name = "ix_delivery_receipts_notification_id", columnList = "notification_id"),
        @Index(name = "ix_delivery_receipts_provider_ref", columnList = "provider_ref")
})
public class DeliveryReceipt extends BaseEntity {

    @Column(name = "notification_id", nullable = false)
    private UUID notificationId;

    @Column(name = "provider", nullable = false)
    private String provider;

    @Column(name = "provider_ref")
    private String providerRef;

    @Column(name = "status_code")
    private String statusCode;

    @Column(name = "status_text")
    private String statusText;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw", columnDefinition = "jsonb")
    private String raw;

    public UUID getNotificationId() { return notificationId; }
    public void setNotificationId(UUID notificationId) { this.notificationId = notificationId; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getProviderRef() { return providerRef; }
    public void setProviderRef(String providerRef) { this.providerRef = providerRef; }

    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String statusCode) { this.statusCode = statusCode; }

    public String getStatusText() { return statusText; }
    public void setStatusText(String statusText) { this.statusText = statusText; }

    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }

    public String getRaw() { return raw; }
    public void setRaw(String raw) { this.raw = raw; }

}
