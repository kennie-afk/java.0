package com.soko.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Append-only record of every event that moves what Soko-the-platform is
 * owed or has been paid: a commission accruing, a subscription fee or an
 * invoice being issued, a payment landing. Nothing here is ever updated or
 * deleted -- a correction is a new offsetting entry, never an edit -- which
 * is what makes {@code sum(entries for an invoice) == invoice.totalCents}
 * a real proof rather than an assertion (see InvoiceServiceTest).
 */
@Entity
@Table(name = "platform_ledger")
public class LedgerEntry {

    @Id @GeneratedValue private UUID id;

    @Column(name = "tenant_id", nullable = false) private UUID tenantId;
    @Column(name = "entry_type", nullable = false) private String entryType;
    @Column(name = "reference_type", nullable = false) private String referenceType;
    @Column(name = "reference_id", nullable = false) private UUID referenceId;
    @Column(name = "amount_cents", nullable = false) private long amountCents;
    @Column private String description;
    @Column(name = "created_at", nullable = false) private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID v) { this.id = v; }
    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID v) { this.tenantId = v; }
    public String getEntryType() { return entryType; }
    public void setEntryType(String v) { this.entryType = v; }
    public String getReferenceType() { return referenceType; }
    public void setReferenceType(String v) { this.referenceType = v; }
    public UUID getReferenceId() { return referenceId; }
    public void setReferenceId(UUID v) { this.referenceId = v; }
    public long getAmountCents() { return amountCents; }
    public void setAmountCents(long v) { this.amountCents = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant v) { this.createdAt = v; }
}
