package com.smartseason.inventory.web.dto;

import com.smartseason.inventory.domain.InputIssue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InputIssueUpdateRequest(
        UUID farmId,
        UUID plotId,
        UUID seasonId,
        @Size(max = 255) String inputCode,
        @Size(max = 255) String inputName,
        BigDecimal quantity,
        @Size(max = 255) String unit,
        UUID issuedTo,
        UUID issuedBy,
        Instant issuedAt,
        BigDecimal unitCost,
        BigDecimal expectedRatePerHa,
        InputIssue.Status status) {
}
