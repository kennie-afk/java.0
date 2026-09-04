package com.kenyarealestate.pms.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.util.List;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RaiseMaintenanceRequest {
    private UUID unitId;
    @NotBlank @Size(max = 32) private String category;
    private String priority;
    @NotBlank @Size(max = 160) private String title;
    @NotBlank @Size(max = 4000) private String description;
    @Builder.Default private List<String> imageUrls = List.of();
}
