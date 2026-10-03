package com.hms.programmes;

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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ProgrammeModels {
    private ProgrammeModels() {}

    public static final String PROGRAMMES = "HIV|TB|HYPERTENSION|DIABETES|ASTHMA|EPILEPSY";

    public record EnrolInput(@NotNull UUID facilityId, @NotNull UUID patientId, @NotBlank @Pattern(regexp = PROGRAMMES) String programme, LocalDate enrolledOn,
                             @Size(max = 200) String regimen, LocalDate nextVisitOn) {}

    public record VisitInput(@NotNull LocalDate visitedOn, @DecimalMin("0.3") @DecimalMax("400") BigDecimal weightKg, @Min(40) @Max(300) Integer systolic,
                             @Min(20) @Max(200) Integer diastolic, @DecimalMin("0.5") @DecimalMax("60") BigDecimal glucoseMmol, @Pattern(regexp = "GOOD|FAIR|POOR") String adherence,
                             @Size(max = 200) String regimen, LocalDate nextVisitOn, @Size(max = 1000) String notes) {}

    public record OutcomeInput(@NotBlank @Pattern(regexp = "TRANSFERRED_OUT|LOST_TO_FOLLOW_UP|COMPLETED|DIED|STOPPED") String status, LocalDate outcomeOn,
                               @NotBlank @Size(min = 3, max = 500) String note) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Visit(UUID id, LocalDate visitedOn, BigDecimal weightKg, Integer systolic, Integer diastolic, BigDecimal glucoseMmol, String adherence, String regimen,
                        LocalDate nextVisitOn, String notes, Instant recordedAt) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Enrolment(UUID id, UUID facilityId, UUID patientId, String patientName, String programme, String registerNo, LocalDate enrolledOn, String status, String regimen,
                            LocalDate nextVisitOn, LocalDate outcomeOn, String outcomeNote, Integer daysOverdue, List<Visit> visits) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Row(UUID id, UUID patientId, String patientName, String programme, String registerNo, LocalDate enrolledOn, String status, LocalDate nextVisitOn, Integer daysOverdue) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Defaulter(UUID id, UUID patientId, String patientName, String phone, String programme, String registerNo, LocalDate nextVisitOn, int daysOverdue) {}

    public record ProgrammeCount(String programme, int active, int missedVisit, int outcomesInPeriod) {}

    public record Summary(UUID facilityId, LocalDate from, LocalDate to, int graceDays, List<ProgrammeCount> programmes) {}
}
