package com.hms.portal;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class PortalModels {
    private PortalModels() {}

    public record ActivateInput(@NotBlank @Size(max = 60) String organisation, @NotBlank @Size(min = 8, max = 20) String code, @NotNull LocalDate birthDate,
                                @NotBlank @Size(max = 120) String login, @NotBlank @Size(min = 10, max = 100) String password) {}

    public record LoginInput(@NotBlank @Size(max = 60) String organisation, @NotBlank @Size(max = 120) String login, @NotBlank @Size(max = 100) String password) {}

    public record Session(String token, long expiresInSeconds, String patientName, String organisationName) {}

    public record InviteInput(@NotNull UUID patientId) {}

    public record Invitation(String code, Instant expiresAt, String patientName, java.util.List<String> notices) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AccountInfo(boolean hasAccount, String status, String login, Instant lastLoginAt) {}

    public record RequestInput(@NotNull UUID facilityId, @NotNull LocalDate preferredDate, @NotBlank @Size(min = 3, max = 500) String reason) {}

    public record ResolveInput(@NotBlank @Pattern(regexp = "SCHEDULED|DECLINED") String status, @NotBlank @Size(min = 3, max = 500) String note) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Me(String givenName, String familyName, LocalDate birthDate, String phone, String email) {}

    public record FacilityRef(UUID id, String name) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record LabResult(UUID id, String test, BigDecimal valueNumeric, String valueText, String unit, String flag, BigDecimal refLow, BigDecimal refHigh, Instant validatedAt, Instant releasedAt) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ImagingReport(UUID id, String procedure, String modality, String findings, String impression, Instant signedAt, Instant releasedAt) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Appointment(UUID id, String facility, Instant startsAt, String status, String reason) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record AppointmentRequest(UUID id, UUID facilityId, String facility, String patientName, LocalDate preferredDate, String reason, String status, String responseNote, Instant createdAt) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Medication(UUID id, String drug, String dose, String route, String frequency, Integer durationDays, String status, Instant prescribedAt) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Allergy(UUID id, String substance, String reaction, String severity) {}
}
