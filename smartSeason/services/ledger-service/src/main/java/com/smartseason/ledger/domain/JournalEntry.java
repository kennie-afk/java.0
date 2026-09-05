package com.smartseason.ledger.domain;

import com.smartseason.ledger.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "journal_entries", indexes = {
        @Index(name = "ix_journal_entries_entry_number", columnList = "entry_number"),
        @Index(name = "ix_journal_entries_source_ref", columnList = "source_ref"),
        @Index(name = "ix_journal_entries_idempotency_key", columnList = "idempotency_key")
})
public class JournalEntry extends BaseEntity {

    @Column(name = "entry_number", nullable = false, unique = true)
    private String entryNumber;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "source_event")
    private String sourceEvent;

    @Column(name = "source_ref")
    private String sourceRef;

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "total_debit", nullable = false)
    private BigDecimal totalDebit;

    @Column(name = "total_credit", nullable = false)
    private BigDecimal totalCredit;

    @Column(name = "balanced", nullable = false)
    private Boolean balanced;

    @Column(name = "reversal_of_id")
    private UUID reversalOfId;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    public String getEntryNumber() { return entryNumber; }
    public void setEntryNumber(String entryNumber) { this.entryNumber = entryNumber; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getSourceEvent() { return sourceEvent; }
    public void setSourceEvent(String sourceEvent) { this.sourceEvent = sourceEvent; }

    public String getSourceRef() { return sourceRef; }
    public void setSourceRef(String sourceRef) { this.sourceRef = sourceRef; }

    public Instant getPostedAt() { return postedAt; }
    public void setPostedAt(Instant postedAt) { this.postedAt = postedAt; }

    public LocalDate getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(LocalDate effectiveDate) { this.effectiveDate = effectiveDate; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public BigDecimal getTotalDebit() { return totalDebit; }
    public void setTotalDebit(BigDecimal totalDebit) { this.totalDebit = totalDebit; }

    public BigDecimal getTotalCredit() { return totalCredit; }
    public void setTotalCredit(BigDecimal totalCredit) { this.totalCredit = totalCredit; }

    public Boolean getBalanced() { return balanced; }
    public void setBalanced(Boolean balanced) { this.balanced = balanced; }

    public UUID getReversalOfId() { return reversalOfId; }
    public void setReversalOfId(UUID reversalOfId) { this.reversalOfId = reversalOfId; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

}
