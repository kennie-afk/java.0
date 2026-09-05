package com.smartseason.identity.web.dto;

import com.smartseason.identity.domain.Organisation;
import java.time.Instant;
import java.util.UUID;

public record OrganisationResponse(
        UUID id,
        String name,
        Organisation.OrgType orgType,
        String county,
        String registrationNo,
        String phone,
        String email,
        Organisation.Status status,
        Organisation.KycStatus kycStatus,
        Instant createdAt,
        Instant updatedAt) {

    public static OrganisationResponse from(Organisation entity) {
        return new OrganisationResponse(
                entity.getId(),
                entity.getName(),
                entity.getOrgType(),
                entity.getCounty(),
                entity.getRegistrationNo(),
                entity.getPhone(),
                entity.getEmail(),
                entity.getStatus(),
                entity.getKycStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
