package com.smartseason.deviceregistry.web.dto;

import com.smartseason.deviceregistry.domain.FirmwareRelease;
import java.time.Instant;
import java.util.UUID;

public record FirmwareReleaseResponse(
        UUID id,
        String deviceType,
        String releaseVersion,
        String artifactUrl,
        String checksum,
        String releaseNotes,
        Boolean mandatory,
        Instant publishedAt,
        Instant createdAt,
        Instant updatedAt) {

    public static FirmwareReleaseResponse from(FirmwareRelease entity) {
        return new FirmwareReleaseResponse(
                entity.getId(),
                entity.getDeviceType(),
                entity.getReleaseVersion(),
                entity.getArtifactUrl(),
                entity.getChecksum(),
                entity.getReleaseNotes(),
                entity.getMandatory(),
                entity.getPublishedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
