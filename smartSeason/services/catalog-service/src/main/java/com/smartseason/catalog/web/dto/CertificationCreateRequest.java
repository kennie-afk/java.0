package com.smartseason.catalog.web.dto;

import com.smartseason.catalog.domain.Certification;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CertificationCreateRequest(
        @NotBlank @Size(max = 255) String code,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String issuingBody,
        String description,
        Integer validityMonths) {
}
