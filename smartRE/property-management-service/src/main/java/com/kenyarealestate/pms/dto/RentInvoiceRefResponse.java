package com.kenyarealestate.pms.dto;

import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RentInvoiceRefResponse {
    private UUID invoiceId;
    private UUID leaseId;
    private UUID unitId;
    private UUID tenantId;
    private UUID tenantUserId;
    private UUID landlordId;
    private UUID propertyId;
    private String invoiceNumber;
    private BigDecimal balance;
    private String status;
}
