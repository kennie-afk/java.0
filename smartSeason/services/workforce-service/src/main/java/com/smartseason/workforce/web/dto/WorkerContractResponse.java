package com.smartseason.workforce.web.dto;

import com.smartseason.workforce.domain.WorkerContract;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WorkerContractResponse(
        UUID id,
        UUID workerId,
        UUID farmId,
        WorkerContract.ContractType contractType,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal dailyRate,
        BigDecimal pieceRate,
        String pieceUnit,
        UUID supervisorId,
        WorkerContract.Status status,
        String terms,
        Instant createdAt,
        Instant updatedAt) {

    public static WorkerContractResponse from(WorkerContract entity) {
        return new WorkerContractResponse(
                entity.getId(),
                entity.getWorkerId(),
                entity.getFarmId(),
                entity.getContractType(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getDailyRate(),
                entity.getPieceRate(),
                entity.getPieceUnit(),
                entity.getSupervisorId(),
                entity.getStatus(),
                entity.getTerms(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
