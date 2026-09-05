package com.smartseason.audit.domain;

import com.smartseason.audit.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "audit_anchors")
public class AuditAnchor extends BaseEntity {

    @Column(name = "anchor_sequence", nullable = false)
    private Long anchorSequence;

    @Column(name = "chain_hash", nullable = false)
    private String chainHash;

    @Column(name = "record_count", nullable = false)
    private Long recordCount;

    @Column(name = "anchored_at", nullable = false)
    private Instant anchoredAt;

    @Column(name = "external_ref")
    private String externalRef;

    public Long getAnchorSequence() { return anchorSequence; }
    public void setAnchorSequence(Long anchorSequence) { this.anchorSequence = anchorSequence; }

    public String getChainHash() { return chainHash; }
    public void setChainHash(String chainHash) { this.chainHash = chainHash; }

    public Long getRecordCount() { return recordCount; }
    public void setRecordCount(Long recordCount) { this.recordCount = recordCount; }

    public Instant getAnchoredAt() { return anchoredAt; }
    public void setAnchoredAt(Instant anchoredAt) { this.anchoredAt = anchoredAt; }

    public String getExternalRef() { return externalRef; }
    public void setExternalRef(String externalRef) { this.externalRef = externalRef; }

}
