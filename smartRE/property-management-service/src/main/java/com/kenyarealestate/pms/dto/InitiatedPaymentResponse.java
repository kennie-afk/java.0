package com.kenyarealestate.pms.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data @NoArgsConstructor @AllArgsConstructor
public class InitiatedPaymentResponse {
    private UUID id;
    private String status;
    private BigDecimal amount;
    private String phoneNumber;
}
