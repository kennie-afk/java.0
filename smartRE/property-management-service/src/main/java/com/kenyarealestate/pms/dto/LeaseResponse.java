package com.kenyarealestate.pms.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class LeaseResponse {
    private UUID id;
    private UUID unitId;
    private String unitLabel;
    private UUID propertyId;
    private UUID tenantId;
    private String tenantName;
    private String tenantPhone;
    private UUID landlordId;
    private LocalDate startDate;
    private LocalDate endDate;
    private BigDecimal rentAmount;
    private BigDecimal depositAmount;
    private BigDecimal depositHeld;
    private BigDecimal managementFeePct;
    private Integer billingDay;
    private String paymentFrequency;
    private Integer noticePeriodDays;
    private String status;
    private String terminatedReason;
    private LocalDateTime terminatedAt;
    private LocalDateTime createdAt;
}
