package com.smartseason.workforce.web.dto;

import com.smartseason.workforce.domain.WorkerContract;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record WorkerContractUpdateRequest(
        UUID workerId,
        UUID farmId,
        WorkerContract.ContractType contractType,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal dailyRate,
        BigDecimal pieceRate,
        @Size(max = 255) String pieceUnit,
        UUID supervisorId,
        WorkerContract.Status status,
        String terms) {
}
