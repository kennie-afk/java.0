package com.kenyarealestate.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
@Data
public class RentInvoiceRef {
    private UUID invoiceId;
    private UUID leaseId;
    private UUID tenantId;
    private UUID tenantUserId;
    private UUID landlordId;
    private UUID propertyId;
    private String invoiceNumber;
    private BigDecimal balance;
}
