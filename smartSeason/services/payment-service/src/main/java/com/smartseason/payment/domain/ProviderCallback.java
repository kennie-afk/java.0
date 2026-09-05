package com.smartseason.payment.domain;

import com.smartseason.payment.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

@Entity
@Table(name = "provider_callbacks", indexes = {
        @Index(name = "ix_provider_callbacks_provider", columnList = "provider"),
        @Index(name = "ix_provider_callbacks_external_ref", columnList = "external_ref")
})
public class ProviderCallback extends BaseEntity {

    @Column(name = "provider", nullable = false)
    private String provider;

    @Column(name = "callback_type", nullable = false)
    private String callbackType;

    @Column(name = "external_ref")
    private String externalRef;

    @Column(name = "signature")
    private String signature;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "error", columnDefinition = "TEXT")
    private String error;

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getCallbackType() { return callbackType; }
    public void setCallbackType(String callbackType) { this.callbackType = callbackType; }

    public String getExternalRef() { return externalRef; }
    public void setExternalRef(String externalRef) { this.externalRef = externalRef; }

    public String getSignature() { return signature; }
    public void setSignature(String signature) { this.signature = signature; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public Instant getReceivedAt() { return receivedAt; }
    public void setReceivedAt(Instant receivedAt) { this.receivedAt = receivedAt; }

    public Instant getProcessedAt() { return processedAt; }
    public void setProcessedAt(Instant processedAt) { this.processedAt = processedAt; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public enum Status { RECEIVED, PROCESSED, DUPLICATE, INVALID, FAILED }

}
