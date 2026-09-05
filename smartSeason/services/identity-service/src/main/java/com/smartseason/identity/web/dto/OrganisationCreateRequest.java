package com.smartseason.identity.web.dto;

import com.smartseason.identity.domain.Organisation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OrganisationCreateRequest(
        @NotBlank @Size(max = 255) String name,
        @NotNull Organisation.OrgType orgType,
        @Size(max = 255) String county,
        @Size(max = 255) String registrationNo,
        @Size(max = 255) String phone,
        @Size(max = 255) String email,
        @NotNull Organisation.Status status,
        @NotNull Organisation.KycStatus kycStatus) {
}
