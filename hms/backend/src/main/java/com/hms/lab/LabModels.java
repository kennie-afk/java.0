package com.hms.lab;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class LabModels {
    private LabModels() {}

    public record TestInput(@NotBlank @Pattern(regexp = "^[A-Z0-9][A-Z0-9_.-]{0,29}$", message = "upper case letters, digits, . _ -") String code,
                            @NotBlank @Size(min = 2, max = 200) String name, @Size(max = 20) String loincCode, @Size(max = 40) String specimenType,
                            @Pattern(regexp = "NUMERIC|TEXT") String resultType, @Size(max = 30) String unit, BigDecimal refLow, BigDecimal refHigh,
                            BigDecimal criticalLow, BigDecimal criticalHigh, @DecimalMin("0") BigDecimal price, Boolean active) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Test(UUID id, String code, String name, String loincCode, String specimenType, String resultType, String unit, BigDecimal refLow,
                       BigDecimal refHigh, BigDecimal criticalLow, BigDecimal criticalHigh, BigDecimal price, boolean active) {}

    public record OrderInput(@NotNull UUID facilityId, @NotNull UUID patientId, UUID encounterId,
                             @Pattern(regexp = "ROUTINE|URGENT|STAT") String priority, @Size(max = 500) String clinicalInfo,
                             @NotEmpty @Size(max = 40) List<@NotNull UUID> testIds) {}

    public record CollectInput(List<UUID> itemIds) {}

    public record ResultInput(BigDecimal numeric, @Size(max = 2000) String text) {}

    public record AmendInput(BigDecimal numeric, @Size(max = 2000) String text, @NotBlank @Size(min = 5, max = 300) String reason) {}

    public record AckInput(@NotBlank @Size(min = 5, max = 300) String note) {}

    public record CancelInput(@NotBlank @Size(min = 3, max = 300) String reason) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Item(UUID id, UUID testId, String testCode, String testName, String unit, BigDecimal refLow, BigDecimal refHigh, String status,
                       String specimenBarcode, Instant collectedAt, BigDecimal resultNumeric, String resultText, String flag, boolean critical,
                       UUID enteredBy, Instant enteredAt, UUID validatedBy, Instant validatedAt, Instant criticalAckAt, String criticalAckNote, int version,
                       boolean resultHidden) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Order(UUID id, UUID facilityId, UUID patientId, String patientName, UUID encounterId, String orderNumber, String priority, String status,
                        String clinicalInfo, UUID orderedBy, Instant createdAt, List<Item> items) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OrderRow(UUID id, String orderNumber, UUID patientId, String patientName, String priority, String status, Instant createdAt, int items,
                           boolean hasUnacknowledgedCritical) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record HistoryEntry(int version, BigDecimal resultNumeric, String resultText, String flag, UUID enteredBy, Instant enteredAt, String reason) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CriticalRow(UUID itemId, UUID orderId, String orderNumber, UUID patientId, String patientName, String testName, BigDecimal resultNumeric,
                              String unit, String flag, Instant enteredAt) {}
}
