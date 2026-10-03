package com.hms.mch;

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

public final class MchModels {
    private MchModels() {}

    public record PregnancyInput(@NotNull UUID facilityId, @NotNull UUID patientId, @NotNull LocalDate lmp,
                                 @NotNull @Min(1) @Max(20) Integer gravida, @NotNull @Min(0) @Max(19) Integer parity) {}

    public record AncVisitInput(
            LocalDate visitedOn,
            @DecimalMin("20") @DecimalMax("250") BigDecimal weightKg,
            @Min(50) @Max(260) Integer systolic, @Min(30) @Max(160) Integer diastolic,
            @DecimalMin("0") @DecimalMax("60") BigDecimal fundalHeightCm,
            @Min(60) @Max(220) Integer fetalHeartRate,
            @Pattern(regexp = "CEPHALIC|BREECH|TRANSVERSE|NOT_ASSESSED") String presentation,
            @DecimalMin("2") @DecimalMax("20") BigDecimal haemoglobin,
            @Pattern(regexp = "NEGATIVE|POSITIVE|KNOWN_POSITIVE|NOT_TESTED") String hivStatus,
            @Pattern(regexp = "NEGATIVE|REACTIVE|NOT_TESTED") String syphilis,
            @Pattern(regexp = "NEGATIVE|TRACE|1\\+|2\\+|3\\+|NOT_TESTED") String urineProtein,
            Boolean iptpGiven, Boolean tetanusGiven, Boolean ironFolateGiven,
            @Size(max = 2000) String notes, LocalDate nextVisitOn) {}

    public record DeliveryInput(@NotNull LocalDate deliveredOn,
                                @NotNull @Pattern(regexp = "SVD|ASSISTED_VAGINAL|CAESAREAN|BREECH|NOT_APPLICABLE") String mode,
                                @NotNull @Pattern(regexp = "LIVE_BIRTH|STILLBIRTH|MISCARRIAGE") String outcome,
                                @Min(0) @Max(5) Integer babies, @Min(200) @Max(7000) Integer birthWeightG,
                                @Min(0) @Max(10) Integer apgar5, @Min(0) @Max(10000) Integer bloodLossMl,
                                @Size(max = 2000) String complications) {}

    public record DoseInput(@NotNull UUID facilityId, @NotBlank @Size(max = 30) String vaccine, LocalDate givenOn,
                            @Size(max = 60) String batchNo,
                            @Pattern(regexp = "LEFT_THIGH|RIGHT_THIGH|LEFT_ARM|RIGHT_ARM|ORAL") String site) {}

    public record Flag(String code, String level, String message) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Visit(UUID id, int visitNumber, LocalDate visitedOn, int gestationWeeks, int gestationDays, BigDecimal weightKg,
                        Integer systolic, Integer diastolic, BigDecimal fundalHeightCm, Integer fetalHeartRate, String presentation,
                        BigDecimal haemoglobin, String hivStatus, String syphilis, String urineProtein, boolean iptpGiven,
                        boolean tetanusGiven, boolean ironFolateGiven, List<Flag> flags, String notes, LocalDate nextVisitOn) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Delivery(UUID id, LocalDate deliveredOn, int gestationWeeks, String mode, String outcome, int babies,
                           Integer birthWeightG, Integer apgar5, Integer bloodLossMl, String complications) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Pregnancy(UUID id, UUID facilityId, UUID patientId, String patientName, LocalDate lmp, LocalDate edd,
                            int gestationWeeks, int gestationDays, int gravida, int parity, String status, int visitCount,
                            LocalDate lastVisitOn, LocalDate nextVisitOn, boolean overdue, List<Flag> flags,
                            List<Visit> visits, Delivery delivery) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Dose(String vaccine, String label, String antigen, String dueAge, LocalDate dueOn, String status,
                       LocalDate givenOn, String batchNo, String site) {}

    public record Card(UUID patientId, String patientName, LocalDate birthDate, String ageLabel, int given, int total,
                       List<Dose> doses, String scheduleNote) {}

    public record DueDose(UUID patientId, String patientName, LocalDate birthDate, String vaccine, String label,
                          LocalDate dueOn, int daysOverdue, String status, String phone) {}

    public record Recorded(UUID id, Instant recordedAt) {}
}
