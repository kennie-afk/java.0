package com.smartseason.traceability.web.dto;

import com.smartseason.traceability.domain.TraceBatch;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TraceBatchCreateRequest(
        @NotBlank @Size(max = 255) String batchCode,
        @NotBlank @Size(max = 255) String commodityCode,
        UUID farmId,
        UUID plotId,
        UUID seasonId,
        LocalDate harvestedOn,
        @Size(max = 255) String originCounty,
        UUID currentHolderOrgId,
        BigDecimal quantityKg,
        @NotNull TraceBatch.Status status) {
}
