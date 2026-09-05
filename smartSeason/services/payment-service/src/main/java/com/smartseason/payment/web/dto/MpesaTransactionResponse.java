package com.smartseason.payment.web.dto;

import com.smartseason.payment.domain.MpesaTransaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record MpesaTransactionResponse(
        UUID id,
        UUID paymentIntentId,
        String merchantRequestId,
        String checkoutRequestId,
        String mpesaReceiptNumber,
        String phoneNumber,
        BigDecimal amount,
        MpesaTransaction.TransactionType transactionType,
        Integer resultCode,
        String resultDesc,
        Instant transactionDate,
        String accountReference,
        String rawCallback,
        MpesaTransaction.Status status,
        Instant createdAt,
        Instant updatedAt) {

    public static MpesaTransactionResponse from(MpesaTransaction entity) {
        return new MpesaTransactionResponse(
                entity.getId(),
                entity.getPaymentIntentId(),
                entity.getMerchantRequestId(),
                entity.getCheckoutRequestId(),
                entity.getMpesaReceiptNumber(),
                entity.getPhoneNumber(),
                entity.getAmount(),
                entity.getTransactionType(),
                entity.getResultCode(),
                entity.getResultDesc(),
                entity.getTransactionDate(),
                entity.getAccountReference(),
                entity.getRawCallback(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
