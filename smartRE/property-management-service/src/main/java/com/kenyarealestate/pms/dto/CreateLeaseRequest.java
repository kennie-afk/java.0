package com.kenyarealestate.pms.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CreateLeaseRequest {
    @NotNull private UUID unitId;
    @NotNull private UUID tenantId;
    @NotNull private LocalDate startDate;
    private LocalDate endDate;
    @DecimalMin("0.0") private BigDecimal rentAmount;
    @DecimalMin("0.0") private BigDecimal depositAmount;
    @DecimalMin("0.0") @DecimalMax("100.0") private BigDecimal managementFeePct;
    @Min(1) @Max(28) private Integer billingDay;
    private String paymentFrequency;
    @Min(0) @Max(365) private Integer noticePeriodDays;
}
