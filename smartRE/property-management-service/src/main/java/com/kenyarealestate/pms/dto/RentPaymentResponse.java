package com.kenyarealestate.pms.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RentPaymentResponse {
    private UUID id;
    private UUID invoiceId;
    private UUID paymentId;
    private BigDecimal amount;
    private String method;
    private String status;
    private String mpesaReceipt;
    private String note;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;
}
