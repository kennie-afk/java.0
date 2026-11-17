package com.smartseason.agronomy.casework;

import com.smartseason.agronomy.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "case_messages")
public class CaseMessage extends BaseEntity {

    @Column(name = "case_id", nullable = false)
    private UUID caseId;
    @Column(name = "author_id", nullable = false)
    private UUID authorId;
    @Column(name = "author_name")
    private String authorName;
    @Column(name = "author_role", nullable = false)
    private String authorRole;
    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    public UUID getCaseId() { return caseId; }
    public void setCaseId(UUID v) { this.caseId = v; }
    public UUID getAuthorId() { return authorId; }
    public void setAuthorId(UUID v) { this.authorId = v; }
    public String getAuthorName() { return authorName; }
    public void setAuthorName(String v) { this.authorName = v; }
    public String getAuthorRole() { return authorRole; }
    public void setAuthorRole(String v) { this.authorRole = v; }
    public String getBody() { return body; }
    public void setBody(String v) { this.body = v; }
}
