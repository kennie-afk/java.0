package com.smartseason.inventory.web.dto;

import com.smartseason.inventory.domain.InputIssue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InputIssueCreateRequest(
        @NotNull UUID farmId,
        UUID plotId,
        UUID seasonId,
        @NotBlank @Size(max = 255) String inputCode,
        @NotBlank @Size(max = 255) String inputName,
        @NotNull BigDecimal quantity,
        @NotBlank @Size(max = 255) String unit,
        UUID issuedTo,
        UUID issuedBy,
        @NotNull Instant issuedAt,
        BigDecimal unitCost,
        BigDecimal expectedRatePerHa,
        @NotNull InputIssue.Status status) {
}
