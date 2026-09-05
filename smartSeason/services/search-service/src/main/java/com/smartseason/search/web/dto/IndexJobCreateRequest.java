package com.smartseason.search.web.dto;

import com.smartseason.search.domain.IndexJob;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record IndexJobCreateRequest(
        @NotBlank @Size(max = 255) String indexName,
        @NotNull IndexJob.JobType jobType,
        @Size(max = 255) String sourceEvent,
        @NotNull Integer documentsProcessed,
        @NotNull Instant startedAt,
        Instant completedAt,
        @NotNull IndexJob.Status status,
        String error) {
}
