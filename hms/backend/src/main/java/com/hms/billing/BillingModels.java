package com.hms.billing;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class BillingModels {
    private BillingModels() {}

    public record ChargeInput(@NotBlank @Pattern(regexp = "^[A-Z0-9][A-Z0-9_.-]{0,29}$") String code, @NotBlank @Size(min = 2, max = 200) String name,
                              @NotNull @Pattern(regexp = "CONSULTATION|LAB|PHARMACY|IMAGING|PROCEDURE|BED|OTHER") String category,
                              @NotNull @DecimalMin("0") BigDecimal price, Boolean active) {}

    public record Charge(UUID id, String code, String name, String category, BigDecimal price, boolean active) {}

    public record InvoiceInput(@NotNull UUID facilityId, @NotNull UUID patientId, UUID encounterId, @Pattern(regexp = "CASH|SHA|INSURER") String payerType,
                               @Size(max = 200) String payerName) {}

    public record LineInput(UUID chargeId, @Size(max = 300) String description, @DecimalMin("0") BigDecimal unitPrice, @NotNull @DecimalMin("0.01") BigDecimal quantity) {}

    public record VoidInput(@NotBlank @Size(min = 5, max = 300) String reason) {}

    public record PaymentInput(@NotNull @Pattern(regexp = "CASH|CARD|BANK") String method, @NotNull @DecimalMin("0.01") BigDecimal amount,
                               @Size(max = 100) String reference, @NotBlank @Size(min = 8, max = 100) String idempotencyKey) {}

    public record StkInput(@NotBlank @Size(max = 30) String phone, @NotNull @DecimalMin("1") BigDecimal amount, @NotBlank @Size(min = 8, max = 100) String idempotencyKey) {}

    public record MockCompletion(@NotBlank String checkoutRequestId, @NotNull Boolean success, @Size(max = 30) String receiptNumber, @Size(max = 200) String failureReason) {}

    public record ReverseInput(@NotBlank @Size(min = 5, max = 300) String reason) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Line(UUID id, String description, String sourceType, UUID sourceId, BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineTotal) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Payment(UUID id, UUID invoiceId, String method, BigDecimal amount, String status, String reference, String mpesaPhone, String mpesaCheckoutId,
                          String mpesaReceipt, String failureReason, String receiptNumber, Instant createdAt, Instant completedAt, String reversalReason) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Invoice(UUID id, UUID facilityId, UUID patientId, String patientName, UUID encounterId, String invoiceNumber, String status, String payerType,
                          String payerName, BigDecimal total, BigDecimal amountPaid, BigDecimal balance, String currency, Instant issuedAt, String voidReason, int version,
                          Instant createdAt, List<Line> lines, List<Payment> payments) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InvoiceRow(UUID id, String invoiceNumber, UUID patientId, String patientName, String status, String payerType, BigDecimal total, BigDecimal amountPaid,
                             Instant createdAt) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record StkResponse(Payment payment, String note) {}
}
