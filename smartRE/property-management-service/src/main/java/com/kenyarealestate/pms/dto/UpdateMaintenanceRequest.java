package com.kenyarealestate.pms.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UpdateMaintenanceRequest {
    private String status;
    private String priority;
    @Size(max = 160) private String assignedTo;
    @Size(max = 4000) private String resolutionNotes;
    @DecimalMin("0.0") private BigDecimal cost;
}
