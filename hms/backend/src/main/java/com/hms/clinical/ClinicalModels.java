package com.hms.clinical;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ClinicalModels {
    private ClinicalModels() {}

    static final String ICD11 = "^[0-9A-Z]{4}(\\.[0-9A-Z]{1,2})?$";

    public record OpenEncounter(@NotNull UUID facilityId, @NotNull UUID patientId, @NotNull @Pattern(regexp = "OPD|ED|IPD") String type,
                         UUID appointmentId, @Size(max = 500) String chiefComplaint) {}

    public record Triage(@NotNull @Pattern(regexp = "EMERGENCY|PRIORITY|ROUTINE") String category,
                  @NotBlank @Size(max = 500) String chiefComplaint) {}

    public record VitalsInput(@DecimalMin("25") @DecimalMax("45") BigDecimal tempC, @Min(0) @Max(300) Integer pulse,
                       @Min(0) @Max(100) Integer respRate, @Min(20) @Max(350) Integer systolic, @Min(10) @Max(250) Integer diastolic,
                       @Min(0) @Max(100) Integer spo2, @DecimalMin("0.2") @DecimalMax("700") BigDecimal weightKg,
                       @DecimalMin("20") @DecimalMax("280") BigDecimal heightCm, @DecimalMin("3") @DecimalMax("60") BigDecimal muacCm,
                       @DecimalMin("0.5") @DecimalMax("100") BigDecimal glucoseMmol, @Min(0) @Max(10) Integer painScore) {}

    public record Retract(@NotBlank @Size(min = 5, max = 300) String reason) {}

    public record NoteInput(@NotNull @Pattern(regexp = "SOAP|PROGRESS|ADMISSION|DISCHARGE|PROCEDURE|NURSING|OTHER") String kind,
                     @NotBlank @Size(max = 20000) String body) {}

    public record Amend(@NotBlank @Size(max = 20000) String body, @NotBlank @Size(min = 5, max = 300) String reason) {}

    public record DiagnosisInput(@NotBlank @Pattern(regexp = ICD11, message = "an ICD-11 code such as 1A00 or BA00.0") String icd11Code,
                          @NotBlank @Size(min = 2, max = 300) String title,
                          @Pattern(regexp = "PRIMARY|SECONDARY") String kind,
                          @Pattern(regexp = "PROVISIONAL|CONFIRMED|RULED_OUT") String certainty) {}

    public record DiagnosisUpdate(@NotNull @Pattern(regexp = "PROVISIONAL|CONFIRMED|RULED_OUT") String certainty,
                           @NotNull @Pattern(regexp = "PRIMARY|SECONDARY") String kind) {}

    public record AllergyInput(@NotBlank @Size(min = 2, max = 120) String substance,
                        @Pattern(regexp = "DRUG|FOOD|ENVIRONMENT|OTHER") String category, @Size(max = 300) String reaction,
                        @NotNull @Pattern(regexp = "MILD|MODERATE|SEVERE|LIFE_THREATENING") String severity) {}

    public record AllergyStatus(@NotNull @Pattern(regexp = "ACTIVE|INACTIVE|ENTERED_IN_ERROR") String status, @Size(max = 300) String reason) {}

    public record OrderInput(@NotNull @Pattern(regexp = "MEDICATION|PROCEDURE|IMAGING|REFERRAL|OTHER") String kind,
                      @Pattern(regexp = "ROUTINE|URGENT|STAT") String priority,
                      @NotBlank @Size(min = 2, max = 500) String description,
                      UUID drugId, @Size(max = 200) String drugName, @Size(max = 80) String dose, @Size(max = 40) String route,
                      @Size(max = 80) String frequency, @Min(1) @Max(365) Integer durationDays,
                      @DecimalMin("0.01") BigDecimal quantity, @Size(max = 500) String instructions,
                      /** Confirms a clinician has seen an allergy warning and prescribes anyway. */
                      @Size(max = 300) String allergyOverrideReason) {}

    public record CancelOrder(@NotBlank @Size(min = 3, max = 300) String reason) {}

    public record CloseInput(@Size(max = 300) String noDiagnosisReason) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Encounter(UUID id, UUID facilityId, UUID patientId, String type, String status, UUID attendingId, UUID appointmentId,
                     String chiefComplaint, String triageCategory, Instant triagedAt, Instant startedAt, Instant endedAt, int version) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Vitals(UUID id, Instant recordedAt, UUID recordedBy, BigDecimal tempC, Integer pulse, Integer respRate, Integer systolic,
                  Integer diastolic, Integer spo2, BigDecimal weightKg, BigDecimal heightCm, BigDecimal muacCm, BigDecimal glucoseMmol,
                  Integer painScore, BigDecimal bmi, boolean retracted, String retractReason, List<String> alerts) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Note(UUID id, UUID threadId, int version, String kind, String body, UUID authorId, Instant createdAt, String amendReason) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Diagnosis(UUID id, String icd11Code, String title, String kind, String certainty, UUID recordedBy, Instant createdAt) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Allergy(UUID id, String substance, String category, String reaction, String severity, String status, String statusReason,
                   Instant createdAt) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Order(UUID id, String kind, String status, String priority, String description, UUID drugId, String drugName, String dose,
                 String route, String frequency, Integer durationDays, BigDecimal quantity, BigDecimal dispensedQuantity,
                 String instructions, String allergyOverrideReason, UUID orderedBy, String cancelReason, Instant createdAt, int version,
                 List<String> allergyWarnings) {}

    public record EncounterDetail(Encounter encounter, List<Vitals> vitals, List<Note> notes, List<Diagnosis> diagnoses, List<Order> orders,
                           List<Allergy> allergies) {}
}
