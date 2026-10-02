package com.hms.platform.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Everything configurable lives here, validated at startup, with no insecure defaults. */
@Validated
@ConfigurationProperties(prefix = "hms")
public record HmsProperties(@Valid Jwt jwt, Cors cors, Roles roles, @Valid Security security) {

    public record Jwt(@NotBlank @Size(min = 32, message = "HMS_JWT_SECRET must be at least 32 characters") String secret,
                      @Min(1) int ttlMinutes) {}

    public record Cors(String allowedOrigins) {}

    public record Roles(@Min(0) long cacheTtlMs) {}

    public record Security(boolean allowPrivilegedDbRole, boolean trustForwardedFor,
                           @Min(1) int loginPerMinute, @Min(1) int onboardingPerHour) {}
}
