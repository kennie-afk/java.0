package com.kenyarealestate.pms.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;

/**
 * Arithmetic for the monthly rental income tax return aid. Pure and free of any KRA system: it only
 * multiplies a landlord's own collected rent by a rate that is configured, never assumed.
 */
public final class MriCalculator {

    /** The return and payment are due on the 20th of the month after the rent was received. */
    public static final int DUE_DAY_OF_NEXT_MONTH = 20;

    private MriCalculator() {
    }

    public static BigDecimal tax(BigDecimal grossRent, BigDecimal ratePercent) {
        if (grossRent == null || ratePercent == null) {
            throw new IllegalArgumentException("gross rent and rate are required");
        }
        if (grossRent.signum() < 0 || ratePercent.signum() < 0 || ratePercent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("gross rent must not be negative and the rate must be 0-100");
        }
        return grossRent.multiply(ratePercent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    public static LocalDate dueDate(YearMonth rentMonth) {
        return rentMonth.plusMonths(1).atDay(DUE_DAY_OF_NEXT_MONTH);
    }

    /** Whole days until the due date; negative once it has passed. */
    public static long daysUntilDue(YearMonth rentMonth, LocalDate today) {
        return java.time.temporal.ChronoUnit.DAYS.between(today, dueDate(rentMonth));
    }

    /** True when annualising this month's rent lands inside the band the configured scheme applies to. */
    public static boolean withinBand(BigDecimal monthlyGross, BigDecimal annualLow, BigDecimal annualHigh) {
        BigDecimal annual = monthlyGross.multiply(BigDecimal.valueOf(12));
        return annual.compareTo(annualLow) >= 0 && annual.compareTo(annualHigh) <= 0;
    }
}
