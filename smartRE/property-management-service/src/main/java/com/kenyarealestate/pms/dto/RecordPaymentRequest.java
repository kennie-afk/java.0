package com.kenyarealestate.pms.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RecordPaymentRequest {
    @NotNull @DecimalMin(value = "0.0", inclusive = false) private BigDecimal amount;
    @NotBlank private String method;
    @Size(max = 40) private String mpesaReceipt;
    @Size(max = 500) private String note;
}
