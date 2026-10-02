package com.hms.registry;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request and response shapes for the registry. */
final class PatientModels {
    private PatientModels() {}

    static final String SEX = "MALE|FEMALE|INTERSEX|UNKNOWN";
    static final String MARITAL = "SINGLE|MARRIED|DIVORCED|WIDOWED|SEPARATED|UNKNOWN";
    static final String ID_SYSTEM = "NATIONAL_ID|PASSPORT|ALIEN_ID|BIRTH_CERTIFICATE|SHA_NUMBER|NHIF_LEGACY|UPI";

    record IdentifierInput(@NotNull @Pattern(regexp = ID_SYSTEM) String system,
                           @NotBlank @Size(max = 60) String value) {}

    record ContactInput(@NotBlank @Size(min = 2, max = 40) String relationship,
                        @NotBlank @Size(max = 200) String fullName,
                        @Size(max = 30) String phone,
                        Boolean nextOfKin) {}

    record Demographics(@NotBlank @Size(max = 100) String givenName,
                        @Size(max = 100) String otherNames,
                        @NotBlank @Size(max = 100) String familyName,
                        @NotNull @Pattern(regexp = SEX) String sex,
                        @NotNull @PastOrPresent LocalDate birthDate,
                        Boolean birthDateEstimated,
                        @Size(max = 30) String phone,
                        @Size(max = 200) String email,
                        @Size(max = 80) String county,
                        @Size(max = 80) String subCounty,
                        @Size(max = 300) String addressLine,
                        @Pattern(regexp = MARITAL) String maritalStatus,
                        @Size(max = 100) String occupation) {}

    record CreatePatient(@NotNull UUID facilityId,
                         @NotNull @Valid Demographics demographics,
                         List<@Valid IdentifierInput> identifiers,
                         List<@Valid ContactInput> contacts,
                         /** The clerk has seen the possible matches and confirms this is a different person. */
                         boolean confirmNotDuplicate) {}

    record UpdatePatient(@NotNull Integer version, @NotNull @Valid Demographics demographics) {}

    record MergeRequest(@NotNull UUID survivorId, @NotBlank @Size(min = 10, max = 500) String reason) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Identifier(UUID id, String system, String value, UUID facilityId) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Contact(UUID id, String relationship, String fullName, String phone, boolean nextOfKin) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Patient(UUID id, UUID registeredFacilityId, String givenName, String otherNames, String familyName, String sex,
                   LocalDate birthDate, boolean birthDateEstimated, String phone, String email, String county, String subCounty,
                   String addressLine, String maritalStatus, String occupation, String nationality, Instant deceasedAt,
                   boolean active, boolean restricted, UUID mergedInto, int version, Instant createdAt, Instant updatedAt,
                   List<Identifier> identifiers, List<Contact> contacts) {}

    /** A row in a list: enough to recognise a person, never the full chart. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Summary(UUID id, String givenName, String familyName, String sex, LocalDate birthDate, String phone,
                   boolean restricted, String mrn) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Match(UUID id, String givenName, String familyName, LocalDate birthDate, String sex, String phone,
                 double score, String reason, String mrn) {}
}
