package com.hms.pharmacy;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class PharmacyModels {
    private PharmacyModels() {}

    public record DrugInput(@NotBlank @Size(min = 2, max = 200) String genericName, @Size(max = 60) String strength,
                            @NotBlank @Size(min = 2, max = 60) String form, @Size(max = 30) String unit, @Size(max = 40) String ppbCode,
                            @Size(max = 20) String atcCode, Boolean controlled, @DecimalMin("0") @DecimalMax("10000000") BigDecimal unitPrice,
                            @DecimalMin("0") BigDecimal reorderLevel, Boolean active) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Drug(UUID id, String genericName, String strength, String form, String unit, String ppbCode, String atcCode, boolean controlled,
                       BigDecimal unitPrice, BigDecimal reorderLevel, boolean active) {}

    public record Receipt(@NotNull UUID facilityId, @NotNull UUID drugId, @NotBlank @Size(max = 60) String batchNo, @NotNull LocalDate expiryDate,
                          @NotNull @DecimalMin(value = "0.01") BigDecimal quantity, @DecimalMin("0") BigDecimal unitCost, @Size(max = 200) String supplier) {}

    public record Adjustment(@NotNull UUID batchId, @NotNull BigDecimal delta,
                             @NotNull @Pattern(regexp = "ADJUSTMENT|WRITE_OFF|RETURN") String reason,
                             @NotBlank @Size(min = 5, max = 300) String note) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Batch(UUID id, UUID facilityId, UUID drugId, String drugName, String batchNo, LocalDate expiryDate, BigDecimal quantity,
                        BigDecimal unitCost, String supplier, boolean expired) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StockLine(UUID drugId, String genericName, String strength, String form, boolean controlled, BigDecimal usable,
                            BigDecimal expired, BigDecimal reorderLevel, boolean belowReorder) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Movement(long id, UUID batchId, UUID drugId, String drugName, BigDecimal delta, String reason, String refType, UUID refId, String note,
                           UUID practitionerId, UUID witnessId, Instant at) {}

    public record DispenseInput(@NotNull UUID orderId, @NotNull @DecimalMin("0.01") BigDecimal quantity, UUID drugId, UUID witnessId,
                                @Size(max = 300) String allergyOverrideReason) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Dispensing(UUID id, UUID orderId, UUID patientId, UUID drugId, String drugName, BigDecimal quantity, UUID dispensedBy, UUID witnessId,
                             Instant dispensedAt, java.util.List<Source> sources, BigDecimal orderRemaining, String orderStatus) {}

    public record Source(UUID batchId, String batchNo, LocalDate expiryDate, BigDecimal quantity) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PendingOrder(UUID orderId, UUID encounterId, UUID patientId, String patientName, String drugName, UUID drugId, String dose, String frequency,
                               Integer durationDays, BigDecimal quantity, BigDecimal dispensed, String status, String priority, Instant orderedAt) {}
}
