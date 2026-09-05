package com.smartseason.farm.web.dto;

import com.smartseason.farm.domain.Farm;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FarmResponse(
        UUID id,
        String name,
        UUID ownerUserId,
        String county,
        String subCounty,
        String ward,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal totalAreaHa,
        Farm.Status status,
        UUID cooperativeId,
        String registrationNo,
        Instant createdAt,
        Instant updatedAt) {

    public static FarmResponse from(Farm entity) {
        return new FarmResponse(
                entity.getId(),
                entity.getName(),
                entity.getOwnerUserId(),
                entity.getCounty(),
                entity.getSubCounty(),
                entity.getWard(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getTotalAreaHa(),
                entity.getStatus(),
                entity.getCooperativeId(),
                entity.getRegistrationNo(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
