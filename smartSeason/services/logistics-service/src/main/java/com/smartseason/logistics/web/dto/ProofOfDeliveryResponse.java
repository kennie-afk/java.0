package com.smartseason.logistics.web.dto;

import com.smartseason.logistics.domain.ProofOfDelivery;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProofOfDeliveryResponse(
        UUID id,
        UUID transportJobId,
        String receivedBy,
        Instant receivedAt,
        String signatureUrl,
        String photoUrl,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal deliveredWeightKg,
        BigDecimal varianceKg,
        String notes,
        Boolean disputed,
        Instant createdAt,
        Instant updatedAt) {

    public static ProofOfDeliveryResponse from(ProofOfDelivery entity) {
        return new ProofOfDeliveryResponse(
                entity.getId(),
                entity.getTransportJobId(),
                entity.getReceivedBy(),
                entity.getReceivedAt(),
                entity.getSignatureUrl(),
                entity.getPhotoUrl(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getDeliveredWeightKg(),
                entity.getVarianceKg(),
                entity.getNotes(),
                entity.getDisputed(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
