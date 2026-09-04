package com.kenyarealestate.pms.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PayInvoiceRequest {
    @Size(max = 32) private String phoneNumber;
    @DecimalMin(value = "0.0", inclusive = false) private BigDecimal amount;
}
