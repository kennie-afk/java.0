package com.smartseason.identity.web.dto;

import com.smartseason.identity.domain.Organisation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OrganisationUpdateRequest(
        @Size(max = 255) String name,
        Organisation.OrgType orgType,
        @Size(max = 255) String county,
        @Size(max = 255) String registrationNo,
        @Size(max = 255) String phone,
        @Size(max = 255) String email,
        Organisation.Status status,
        Organisation.KycStatus kycStatus) {
}
