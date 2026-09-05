package com.smartseason.workforce.web.dto;

import com.smartseason.workforce.domain.WorkerContract;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record WorkerContractCreateRequest(
        @NotNull UUID workerId,
        @NotNull UUID farmId,
        @NotNull WorkerContract.ContractType contractType,
        @NotNull LocalDate startDate,
        LocalDate endDate,
        BigDecimal dailyRate,
        BigDecimal pieceRate,
        @Size(max = 255) String pieceUnit,
        UUID supervisorId,
        @NotNull WorkerContract.Status status,
        String terms) {
}
