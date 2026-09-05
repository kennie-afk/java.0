package com.smartseason.search.web.dto;

import com.smartseason.search.domain.SearchDocument;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SearchDocumentResponse(
        UUID id,
        String indexName,
        String docId,
        String docType,
        String title,
        String body,
        String keywords,
        String county,
        String commodityCode,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal boost,
        String payload,
        Instant indexedAt,
        SearchDocument.Status status,
        Instant createdAt,
        Instant updatedAt) {

    public static SearchDocumentResponse from(SearchDocument entity) {
        return new SearchDocumentResponse(
                entity.getId(),
                entity.getIndexName(),
                entity.getDocId(),
                entity.getDocType(),
                entity.getTitle(),
                entity.getBody(),
                entity.getKeywords(),
                entity.getCounty(),
                entity.getCommodityCode(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getBoost(),
                entity.getPayload(),
                entity.getIndexedAt(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
