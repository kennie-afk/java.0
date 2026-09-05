package com.smartseason.payment.web.dto;

import com.smartseason.payment.domain.MpesaTransaction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MpesaTransactionCreateRequest(
        UUID paymentIntentId,
        @Size(max = 255) String merchantRequestId,
        @Size(max = 255) String checkoutRequestId,
        @Size(max = 255) String mpesaReceiptNumber,
        @Size(max = 255) String phoneNumber,
        @NotNull BigDecimal amount,
        @NotNull MpesaTransaction.TransactionType transactionType,
        Integer resultCode,
        @Size(max = 255) String resultDesc,
        Instant transactionDate,
        @Size(max = 255) String accountReference,
        String rawCallback,
        @NotNull MpesaTransaction.Status status) {
}
