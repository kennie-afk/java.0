package com.kenyarealestate.pms.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class InvoiceResponse {
    private UUID id;
    private UUID leaseId;
    private UUID unitId;
    private String unitLabel;
    private UUID tenantId;
    private String tenantName;
    private String tenantPhone;
    private String invoiceNumber;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private LocalDate dueDate;
    private BigDecimal amountDue;
    private BigDecimal amountPaid;
    private BigDecimal balance;
    private String status;
    private LocalDateTime createdAt;
}
