package com.smartseason.search.domain;

import com.smartseason.search.platform.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "index_jobs", indexes = {
        @Index(name = "ix_index_jobs_index_name", columnList = "index_name")
})
public class IndexJob extends BaseEntity {

    @Column(name = "index_name", nullable = false)
    private String indexName;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false)
    private JobType jobType;

    @Column(name = "source_event")
    private String sourceEvent;

    @Column(name = "documents_processed", nullable = false)
    private Integer documentsProcessed;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status;

    @Column(name = "error", columnDefinition = "TEXT")
    private String error;

    public String getIndexName() { return indexName; }
    public void setIndexName(String indexName) { this.indexName = indexName; }

    public JobType getJobType() { return jobType; }
    public void setJobType(JobType jobType) { this.jobType = jobType; }

    public String getSourceEvent() { return sourceEvent; }
    public void setSourceEvent(String sourceEvent) { this.sourceEvent = sourceEvent; }

    public Integer getDocumentsProcessed() { return documentsProcessed; }
    public void setDocumentsProcessed(Integer documentsProcessed) { this.documentsProcessed = documentsProcessed; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getError() { return error; }
    public void setError(String error) { this.error = error; }

    public enum JobType { FULL_REBUILD, INCREMENTAL, DELETE }

    public enum Status { QUEUED, RUNNING, COMPLETED, FAILED }

}
