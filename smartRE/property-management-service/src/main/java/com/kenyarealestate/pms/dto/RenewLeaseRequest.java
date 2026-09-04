package com.kenyarealestate.pms.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RenewLeaseRequest {
    @NotNull private LocalDate startDate;
    private LocalDate endDate;
    @DecimalMin("0.0") private BigDecimal rentAmount;
}
