package com.hms.imaging;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class ImagingModels {
    private ImagingModels() {}

    public record ProcedureInput(@NotBlank @Pattern(regexp = "^[A-Z0-9][A-Z0-9_.-]{0,29}$", message = "upper case letters, digits, . _ -") String code,
                                 @NotBlank @Size(min = 2, max = 200) String name, @NotBlank @Pattern(regexp = "XR|US|CT|MR|MG|FL|NM|OTHER") String modality,
                                 @Size(max = 80) String bodyRegion, @DecimalMin("0") BigDecimal price, Boolean active) {}

    public record Procedure(UUID id, String code, String name, String modality, String bodyRegion, BigDecimal price, boolean active) {}

    public record OrderInput(@NotNull UUID facilityId, @NotNull UUID patientId, UUID encounterId, @NotNull UUID procedureId,
                             @Pattern(regexp = "ROUTINE|URGENT|STAT") String priority, @Size(max = 500) String clinicalInfo) {}

    public record PerformInput(@Size(max = 500) String techniqueNote) {}

    public record ReportInput(@Size(min = 3, max = 8000) String findings, @NotBlank @Size(min = 3, max = 2000) String impression, boolean critical,
                              @Size(max = 300) String criticalNote) {}

    public record AmendInput(@Size(min = 3, max = 8000) String findings, @NotBlank @Size(min = 3, max = 2000) String impression, boolean critical,
                             @Size(max = 300) String criticalNote, @NotBlank @Size(min = 5, max = 300) String reason) {}

    public record AckInput(@NotBlank @Size(min = 5, max = 300) String note) {}

    public record CancelInput(@NotBlank @Size(min = 3, max = 300) String reason) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Order(UUID id, UUID facilityId, UUID patientId, String patientName, UUID encounterId, String orderNumber, String priority, String status,
                        UUID procedureId, String procedureCode, String procedureName, String modality, String bodyRegion, String clinicalInfo, UUID orderedBy,
                        Instant createdAt, Instant performedAt, String techniqueNote, String findings, String impression, boolean critical, String criticalNote,
                        UUID reportedBy, Instant reportedAt, UUID signedBy, Instant signedAt, Instant criticalAckAt, String criticalAckNote, int version,
                        boolean reportHidden, Instant releasedAt) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OrderRow(UUID id, String orderNumber, UUID patientId, String patientName, String procedureName, String modality, String priority, String status,
                           Instant createdAt, boolean hasUnacknowledgedCritical) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record HistoryEntry(int version, String findings, String impression, boolean critical, UUID reportedBy, Instant reportedAt, String reason) {}

    public record CriticalRow(UUID orderId, String orderNumber, UUID patientId, String patientName, String procedureName, String criticalNote, Instant reportedAt) {}
}
