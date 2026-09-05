package com.smartseason.search.web.dto;

import com.smartseason.search.domain.SearchDocument;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record SearchDocumentCreateRequest(
        @NotBlank @Size(max = 255) String indexName,
        @NotBlank @Size(max = 255) String docId,
        @NotBlank @Size(max = 255) String docType,
        @Size(max = 255) String title,
        String body,
        String keywords,
        @Size(max = 255) String county,
        @Size(max = 255) String commodityCode,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal boost,
        String payload,
        @NotNull Instant indexedAt,
        @NotNull SearchDocument.Status status) {
}
