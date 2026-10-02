package com.hms.staff;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class StaffModels {
    private StaffModels() {}

    static final String CADRE = "DOCTOR|CLINICAL_OFFICER|NURSE|MIDWIFE|PHARMACIST|PHARMACEUTICAL_TECHNOLOGIST|LAB_TECHNOLOGIST|RADIOGRAPHER|NUTRITIONIST|PHYSIOTHERAPIST|DENTIST|COMMUNITY_HEALTH|RECORDS_OFFICER|ACCOUNTANT|ADMINISTRATIVE";
    static final String LICENCE = "KMPDC|NCK|PPB|KMLTTB|COC|OTHER";

    record CreateStaff(@NotBlank @Email String email, @NotBlank @Size(min = 2, max = 200) String fullName,
                       @NotNull @Pattern(regexp = CADRE) String cadre,
                       @Pattern(regexp = LICENCE) String licenceBody, @Size(max = 40) String licenceNo,
                       @Size(max = 30) String phone,
                       @NotBlank @Size(min = 12, max = 100, message = "must be at least 12 characters") String temporaryPassword,
                       @NotEmpty List<@NotBlank String> roles, @NotEmpty List<@NotNull UUID> facilityIds) {}

    record UpdateStaff(@NotBlank @Size(min = 2, max = 200) String fullName, @NotNull @Pattern(regexp = CADRE) String cadre,
                       @Pattern(regexp = LICENCE) String licenceBody, @Size(max = 40) String licenceNo,
                       @Size(max = 30) String phone) {}

    record Assignment(@NotEmpty List<@NotBlank String> roles, @NotEmpty List<@NotNull UUID> facilityIds) {}

    record ResetPassword(@NotBlank @Size(min = 12, max = 100, message = "must be at least 12 characters") String temporaryPassword) {}

    record ChangePassword(@NotBlank String currentPassword,
                          @NotBlank @Size(min = 12, max = 100, message = "must be at least 12 characters") String newPassword) {}

    record Staff(UUID id, String email, String fullName, String cadre, String licenceBody, String licenceNo, String phone,
                 String status, boolean mustChangePassword, Instant createdAt, List<String> roles, List<UUID> facilityIds) {}

    record StaffRow(UUID id, String email, String fullName, String cadre, String status, String licenceNo) {}

    record RoleInput(@NotBlank @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,39}$", message = "upper case letters, digits and underscores") String key,
                     @NotBlank @Size(min = 2, max = 80) String label, @Size(max = 300) String description,
                     @NotNull List<@NotBlank String> permissions) {}

    record RoleUpdate(@NotBlank @Size(min = 2, max = 80) String label, @Size(max = 300) String description,
                      @NotNull List<@NotBlank String> permissions) {}

    record Role(String key, String label, String description, boolean system, List<String> permissions, long members) {}

    record FacilityInput(@NotBlank @Size(min = 2, max = 200) String name, @Size(max = 20) String mflCode,
                         @Min(1) @Max(6) Integer kephLevel,
                         @Pattern(regexp = "PUBLIC|PRIVATE|FAITH_BASED|NGO") String ownership,
                         @Size(max = 80) String county, @Size(max = 80) String subCounty, @Size(max = 30) String phone,
                         @Size(max = 300) String addressLine) {}

    record FacilityUpdate(@NotBlank @Size(min = 2, max = 200) String name, @Size(max = 20) String mflCode,
                          @Min(1) @Max(6) Integer kephLevel,
                          @Pattern(regexp = "PUBLIC|PRIVATE|FAITH_BASED|NGO") String ownership,
                          @Size(max = 80) String county, @Size(max = 80) String subCounty, @Size(max = 30) String phone,
                          @Size(max = 300) String addressLine, @NotNull Boolean active) {}

    record Facility(UUID id, String name, String mflCode, Integer kephLevel, String ownership, String county,
                    String subCounty, String phone, String addressLine, boolean active) {}
}
