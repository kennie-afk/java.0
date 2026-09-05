package com.smartseason.deviceregistry.web.dto;

import com.smartseason.deviceregistry.domain.FirmwareRelease;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record FirmwareReleaseCreateRequest(
        @NotBlank @Size(max = 255) String deviceType,
        @NotBlank @Size(max = 255) String releaseVersion,
        @NotBlank @Size(max = 255) String artifactUrl,
        @NotBlank @Size(max = 255) String checksum,
        String releaseNotes,
        @NotNull Boolean mandatory,
        Instant publishedAt) {
}
