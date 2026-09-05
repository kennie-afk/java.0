package com.smartseason.search.web.dto;

import com.smartseason.search.domain.IndexJob;
import java.time.Instant;
import java.util.UUID;

public record IndexJobResponse(
        UUID id,
        String indexName,
        IndexJob.JobType jobType,
        String sourceEvent,
        Integer documentsProcessed,
        Instant startedAt,
        Instant completedAt,
        IndexJob.Status status,
        String error,
        Instant createdAt,
        Instant updatedAt) {

    public static IndexJobResponse from(IndexJob entity) {
        return new IndexJobResponse(
                entity.getId(),
                entity.getIndexName(),
                entity.getJobType(),
                entity.getSourceEvent(),
                entity.getDocumentsProcessed(),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.getStatus(),
                entity.getError(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
