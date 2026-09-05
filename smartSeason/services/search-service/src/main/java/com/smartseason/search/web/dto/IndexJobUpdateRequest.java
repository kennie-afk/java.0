package com.smartseason.search.web.dto;

import com.smartseason.search.domain.IndexJob;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record IndexJobUpdateRequest(
        @Size(max = 255) String indexName,
        IndexJob.JobType jobType,
        @Size(max = 255) String sourceEvent,
        Integer documentsProcessed,
        Instant startedAt,
        Instant completedAt,
        IndexJob.Status status,
        String error) {
}
