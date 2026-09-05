package com.smartseason.logistics.web.dto;

import com.smartseason.logistics.domain.ProofOfDelivery;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProofOfDeliveryUpdateRequest(
        UUID transportJobId,
        @Size(max = 255) String receivedBy,
        Instant receivedAt,
        @Size(max = 255) String signatureUrl,
        @Size(max = 255) String photoUrl,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal deliveredWeightKg,
        BigDecimal varianceKg,
        String notes,
        Boolean disputed) {
}
