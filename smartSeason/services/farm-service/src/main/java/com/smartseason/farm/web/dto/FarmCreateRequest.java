package com.smartseason.farm.web.dto;

import com.smartseason.farm.domain.Farm;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record FarmCreateRequest(
        @NotBlank @Size(max = 255) String name,
        UUID ownerUserId,
        @Size(max = 255) String county,
        @Size(max = 255) String subCounty,
        @Size(max = 255) String ward,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal totalAreaHa,
        @NotNull Farm.Status status,
        UUID cooperativeId,
        @Size(max = 255) String registrationNo) {
}
