package com.smartseason.payment.collect;

import com.smartseason.payment.domain.PaymentIntent;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record CollectionRequest(
        UUID orderId,
        UUID payerOrgId,
        UUID payeeOrgId,
        @NotBlank @Size(max = 32) String payerPhone,
        @NotNull @DecimalMin("1.0") BigDecimal amount,
        @NotBlank @Size(max = 3) String currency,
        @NotNull PaymentIntent.Purpose purpose,
        boolean escrow,
        @NotBlank @Size(max = 255) String idempotencyKey,
        @Size(max = 255) String accountReference) {
}
