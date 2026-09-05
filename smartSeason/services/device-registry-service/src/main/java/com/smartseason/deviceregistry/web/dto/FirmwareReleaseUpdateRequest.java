package com.smartseason.deviceregistry.web.dto;

import com.smartseason.deviceregistry.domain.FirmwareRelease;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record FirmwareReleaseUpdateRequest(
        @Size(max = 255) String deviceType,
        @Size(max = 255) String releaseVersion,
        @Size(max = 255) String artifactUrl,
        @Size(max = 255) String checksum,
        String releaseNotes,
        Boolean mandatory,
        Instant publishedAt) {
}
