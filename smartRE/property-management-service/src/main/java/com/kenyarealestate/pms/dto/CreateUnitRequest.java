package com.kenyarealestate.pms.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CreateUnitRequest {
    @NotNull private UUID propertyId;
    @NotBlank @Size(max = 40) private String label;
    @Size(max = 32) private String unitType;
    @Min(0) @Max(50) private Integer bedrooms;
    @Min(0) @Max(50) private Integer bathrooms;
    @DecimalMin("0.0") private BigDecimal sizeSqm;
    @NotNull @DecimalMin("0.0") private BigDecimal rentAmount;
    @DecimalMin("0.0") private BigDecimal depositAmount;
    private String notes;
}
