package com.kenyarealestate.pms.service;

import com.kenyarealestate.pms.entity.Lease;
import com.kenyarealestate.pms.entity.PaymentFrequency;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class BillingPeriodsTest {

    private Lease lease(LocalDate start, int billingDay, PaymentFrequency freq, String rent) {
        return Lease.builder().startDate(start).billingDay(billingDay)
                .paymentFrequency(freq).rentAmount(new BigDecimal(rent)).build();
    }

    @Test
    void theFirstPeriodStartsOnTheBillingDayInTheStartMonthWhenItHasNotPassed() {
        assertEquals(LocalDate.of(2026, 10, 15),
                BillingPeriods.firstPeriodStart(lease(LocalDate.of(2026, 10, 5), 15, PaymentFrequency.MONTHLY, "35000")));
    }

    @Test
    void aBillingDayAlreadyPastRollsToTheNextMonth() {
        assertEquals(LocalDate.of(2026, 11, 3),
                BillingPeriods.firstPeriodStart(lease(LocalDate.of(2026, 10, 20), 3, PaymentFrequency.MONTHLY, "35000")));
    }

    @Test
    void aBillingDayBeyondTheTwentyEighthIsClamped() {
        assertEquals(LocalDate.of(2026, 2, 28),
                BillingPeriods.firstPeriodStart(lease(LocalDate.of(2026, 2, 1), 31, PaymentFrequency.MONTHLY, "35000")),
                "February has no 31st, so a stored day beyond 28 must clamp rather than throw");
    }

    @Test
    void monthlyPeriodsRunToTheDayBeforeTheNext() {
        LocalDate start = LocalDate.of(2026, 10, 5);
        assertEquals(LocalDate.of(2026, 11, 5), BillingPeriods.nextPeriodStart(start, PaymentFrequency.MONTHLY));
        assertEquals(LocalDate.of(2026, 11, 4), BillingPeriods.periodEnd(start, PaymentFrequency.MONTHLY));
    }

    @Test
    void quarterlyAndAnnualPeriodsStepCorrectly() {
        LocalDate start = LocalDate.of(2026, 1, 10);
        assertEquals(LocalDate.of(2026, 4, 10), BillingPeriods.nextPeriodStart(start, PaymentFrequency.QUARTERLY));
        assertEquals(LocalDate.of(2027, 1, 10), BillingPeriods.nextPeriodStart(start, PaymentFrequency.ANNUALLY));
    }

    @Test
    void aQuarterlyLeaseIsBilledThreeMonthsOfRentAtATime() {
        assertEquals(0, new BigDecimal("105000").compareTo(
                BillingPeriods.amountFor(lease(LocalDate.of(2026, 1, 1), 1, PaymentFrequency.QUARTERLY, "35000"))));
    }

    @Test
    void anAnnualLeaseIsBilledTwelveMonthsOfRent() {
        assertEquals(0, new BigDecimal("420000").compareTo(
                BillingPeriods.amountFor(lease(LocalDate.of(2026, 1, 1), 1, PaymentFrequency.ANNUALLY, "35000"))));
    }

    @Test
    void aMonthlyLeaseIsBilledOneMonthOfRent() {
        assertEquals(0, new BigDecimal("35000").compareTo(
                BillingPeriods.amountFor(lease(LocalDate.of(2026, 1, 1), 1, PaymentFrequency.MONTHLY, "35000"))));
    }
}
