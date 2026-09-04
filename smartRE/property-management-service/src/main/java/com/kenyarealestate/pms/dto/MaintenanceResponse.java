package com.kenyarealestate.pms.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class MaintenanceResponse {
    private UUID id;
    private UUID unitId;
    private String unitLabel;
    private UUID leaseId;
    private UUID tenantId;
    private String tenantName;
    private UUID landlordId;
    private String reference;
    private String category;
    private String priority;
    private String title;
    private String description;
    private List<String> imageUrls;
    private String status;
    private String raisedByRole;
    private String assignedTo;
    private String resolutionNotes;
    private BigDecimal cost;
    private LocalDateTime createdAt;
    private LocalDateTime acknowledgedAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;
}
