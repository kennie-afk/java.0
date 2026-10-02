package com.hms.inpatient;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class InpatientModels {
    private InpatientModels() {}

    public record WardInput(@NotNull UUID facilityId, @NotBlank @Size(min = 2, max = 100) String name,
                            @Pattern(regexp = "GENERAL|SURGICAL|MEDICAL|MATERNITY|PAEDIATRIC|NEWBORN|ICU|HDU|ISOLATION|PSYCHIATRIC|OTHER") String kind,
                            @NotNull @Size(min = 1, max = 200) List<@NotBlank @Size(max = 30) String> bedLabels) {}

    public record BedsInput(@NotNull @Size(min = 1, max = 200) List<@NotBlank @Size(max = 30) String> labels) {}

    public record BedStatus(@NotNull @Pattern(regexp = "AVAILABLE|CLEANING|OUT_OF_SERVICE") String status) {}

    public record AdmitInput(@NotNull UUID facilityId, @NotNull UUID patientId, @NotNull UUID bedId, @Size(max = 500) String admittingDiagnosis) {}

    public record TransferInput(@NotNull UUID toBedId, @NotBlank @Size(min = 3, max = 300) String reason) {}

    public record DischargeInput(@NotNull @Pattern(regexp = "DISCHARGED|REFERRED|LAMA|ABSCONDED|DIED") String type,
                                 @NotBlank @Size(min = 10, max = 5000) String summary, @Size(max = 300) String noDiagnosisReason) {}

    public record Ward(UUID id, UUID facilityId, String name, String kind, boolean active, int beds, int occupied, int available, int cleaning, int outOfService) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Bed(UUID id, UUID wardId, String wardName, String label, String status, UUID admissionId, String patientName) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Assignment(UUID bedId, String bedLabel, String wardName, Instant assignedAt, Instant releasedAt, String reason) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Admission(UUID id, UUID facilityId, UUID patientId, String patientName, UUID encounterId, String admissionNumber, String status, String admittingDiagnosis,
                            Instant admittedAt, String dischargeType, String dischargeSummary, Instant dischargedAt, String currentBed, String currentWard,
                            long lengthOfStayDays, int version, List<Assignment> history) {}
}
