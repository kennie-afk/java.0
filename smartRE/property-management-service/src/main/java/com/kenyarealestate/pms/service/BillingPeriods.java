package com.kenyarealestate.pms.service;

import com.kenyarealestate.pms.entity.Lease;
import com.kenyarealestate.pms.entity.PaymentFrequency;

import java.time.LocalDate;

public final class BillingPeriods {

    private BillingPeriods() {}

    public static LocalDate firstPeriodStart(Lease lease) {
        return atBillingDay(lease.getStartDate(), lease.getBillingDay());
    }

    public static LocalDate nextPeriodStart(LocalDate periodStart, PaymentFrequency frequency) {
        return switch (frequency) {
            case MONTHLY   -> periodStart.plusMonths(1);
            case QUARTERLY -> periodStart.plusMonths(3);
            case ANNUALLY  -> periodStart.plusYears(1);
        };
    }

    public static LocalDate periodEnd(LocalDate periodStart, PaymentFrequency frequency) {
        return nextPeriodStart(periodStart, frequency).minusDays(1);
    }

    public static java.math.BigDecimal amountFor(Lease lease) {
        return switch (lease.getPaymentFrequency()) {
            case MONTHLY   -> lease.getRentAmount();
            case QUARTERLY -> lease.getRentAmount().multiply(java.math.BigDecimal.valueOf(3));
            case ANNUALLY  -> lease.getRentAmount().multiply(java.math.BigDecimal.valueOf(12));
        };
    }

    private static LocalDate atBillingDay(LocalDate reference, Integer billingDay) {
        int day = billingDay == null ? 1 : Math.min(Math.max(billingDay, 1), 28);
        LocalDate candidate = reference.withDayOfMonth(day);
        return candidate.isBefore(reference) ? candidate.plusMonths(1) : candidate;
    }
}
