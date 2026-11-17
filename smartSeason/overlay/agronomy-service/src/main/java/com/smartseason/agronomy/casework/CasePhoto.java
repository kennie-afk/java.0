package com.smartseason.agronomy.casework;

import com.smartseason.agronomy.platform.BaseEntity;
import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Table;
import java.util.UUID;

/** A photograph attached to a case. Held apart from the case so listings never load the bytes. */
@Entity
@Table(name = "case_photos")
public class CasePhoto extends BaseEntity {

    @Column(name = "case_id", nullable = false)
    private UUID caseId;
    @Column(name = "content_type", nullable = false)
    private String contentType;
    @Column(name = "size_bytes", nullable = false)
    private int sizeBytes;
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "data", nullable = false)
    private byte[] data;

    public UUID getCaseId() { return caseId; }
    public void setCaseId(UUID v) { this.caseId = v; }
    public String getContentType() { return contentType; }
    public void setContentType(String v) { this.contentType = v; }
    public int getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(int v) { this.sizeBytes = v; }
    public byte[] getData() { return data; }
    public void setData(byte[] v) { this.data = v; }
}
