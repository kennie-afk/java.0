package com.hms.mch;

import static com.hms.mch.MchModels.Flag;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Request and response shapes for postnatal care and family planning. */
public final class PostnatalModels {
    private PostnatalModels() {}

    public record PostnatalVisitInput(
            LocalDate visitedOn,
            @Min(50) @Max(260) Integer systolic, @Min(30) @Max(160) Integer diastolic,
            @DecimalMin("34") @DecimalMax("42") BigDecimal temperatureC,
            @Pattern(regexp = "INVOLUTING|SUBINVOLUTED|NOT_ASSESSED") String uterus,
            @Pattern(regexp = "NORMAL|HEAVY|OFFENSIVE|NOT_ASSESSED") String lochia,
            @Pattern(regexp = "HEALED|INFECTED|NOT_APPLICABLE|NOT_ASSESSED") String wound,
            @Pattern(regexp = "EXCLUSIVE|MIXED|NOT_BREASTFEEDING|NOT_ASSESSED") String breastfeeding,
            Boolean lowMood, Boolean fpCounselled,
            @Min(500) @Max(8000) Integer babyWeightG,
            @DecimalMin("30") @DecimalMax("42") BigDecimal babyTemperatureC,
            @Pattern(regexp = "CLEAN|INFECTED|SEPARATED|NOT_ASSESSED") String cord,
            Boolean jaundice, Boolean feedingWell,
            @Size(max = 2000) String notes, LocalDate nextVisitOn) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PostnatalVisit(UUID id, int visitNumber, LocalDate visitedOn, int daysSinceDelivery, Integer systolic, Integer diastolic,
                                 BigDecimal temperatureC, String uterus, String lochia, String wound, String breastfeeding, boolean lowMood,
                                 boolean fpCounselled, Integer babyWeightG, BigDecimal babyTemperatureC, String cord, Boolean jaundice,
                                 Boolean feedingWell, List<Flag> flags, String notes, LocalDate nextVisitOn) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Postnatal(UUID pregnancyId, UUID patientId, String patientName, LocalDate deliveredOn, String outcome, int babies,
                            int daysSinceDelivery, boolean babyRecorded, LocalDate nextVisitOn, boolean overdue, String scheduleNote,
                            List<PostnatalVisit> visits) {}

    public record FamilyPlanningInput(
            @NotNull UUID facilityId, LocalDate visitedOn,
            @NotNull @Pattern(regexp = "NEW|REVISIT|SWITCH|DISCONTINUE") String visitType,
            @NotNull @Pattern(regexp = "COC|POP|DMPA|IMPLANT|IUCD|MALE_CONDOM|FEMALE_CONDOM|TUBAL_LIGATION|VASECTOMY|NATURAL|EMERGENCY|NONE") String method,
            @Min(50) @Max(260) Integer systolic, @Min(30) @Max(160) Integer diastolic,
            @DecimalMin("20") @DecimalMax("250") BigDecimal weightKg,
            LocalDate nextDueOn, @Size(max = 2000) String notes) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FamilyPlanningVisit(UUID id, UUID facilityId, LocalDate visitedOn, String visitType, String method, String methodLabel,
                                      Integer systolic, Integer diastolic, BigDecimal weightKg, LocalDate nextDueOn, List<Flag> flags, String notes) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record FamilyPlanning(UUID patientId, String patientName, String currentMethod, String currentMethodLabel, LocalDate nextDueOn,
                                 boolean overdue, List<FamilyPlanningVisit> visits) {}

    public record FamilyPlanningDue(UUID patientId, String patientName, String method, String methodLabel, LocalDate nextDueOn, int daysOverdue, String phone) {}
}
