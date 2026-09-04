package com.kenyarealestate.pms.dto;

import lombok.*;

import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PortfolioSummaryResponse {
    private long totalUnits;
    private long occupiedUnits;
    private long vacantUnits;
    private long underMaintenanceUnits;
    private long tenants;
    private long activeLeases;
    private BigDecimal monthlyRentRoll;
    private double occupancyRate;
    private BigDecimal outstandingRent;
    private long overdueInvoices;
    private long openMaintenance;
}
