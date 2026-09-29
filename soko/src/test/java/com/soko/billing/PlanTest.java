package com.soko.billing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlanTest {

    @Test
    void commissionRoundsDownToTheNearestCent() {
        // 3.50% (350 bps) of KSh 1,499.99 (149999 cents) = 5249.965 cents;
        // floor division must not round up to 5250.
        assertThat(Plan.GROWTH.commissionOn(149_999L)).isEqualTo(5249L);
    }

    @Test
    void zeroRevenueOwesZeroCommission() {
        assertThat(Plan.SCALE.commissionOn(0L)).isEqualTo(0L);
    }

    @Test
    void everyPlanHasANonNegativeFeeAndRate() {
        for (Plan plan : Plan.values()) {
            assertThat(plan.monthlyFeeCents()).isGreaterThanOrEqualTo(0L);
            assertThat(plan.commissionBps()).isBetween(0, 10_000);
        }
    }
}
