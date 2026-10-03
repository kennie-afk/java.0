package com.kenyarealestate.pms.service;

import com.kenyarealestate.pms.entity.PaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** One confirmed rent receipt that counts towards a month's rental income. */
public record MriLine(
        UUID paymentId,
        String invoiceNumber,
        String unitLabel,
        UUID propertyId,
        String tenantName,
        PaymentMethod method,
        BigDecimal amount,
        LocalDateTime paidAt,
        String mpesaReceipt) {
}
