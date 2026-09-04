package com.kenyarealestate.pms.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UnitResponse {
    private UUID id;
    private UUID propertyId;
    private UUID landlordId;
    private String label;
    private String unitType;
    private Integer bedrooms;
    private Integer bathrooms;
    private BigDecimal sizeSqm;
    private BigDecimal rentAmount;
    private BigDecimal depositAmount;
    private String status;
    private String notes;
    private UUID activeLeaseId;
    private String activeTenantName;
    private LocalDateTime createdAt;
}
