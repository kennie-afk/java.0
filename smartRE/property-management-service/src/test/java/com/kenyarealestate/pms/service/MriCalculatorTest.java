package com.kenyarealestate.pms.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class MriCalculatorTest {

    @Test
    void taxIsGrossTimesTheGivenRateRoundedToTheCent() {
        assertThat(MriCalculator.tax(new BigDecimal("100000"), new BigDecimal("7.5"))).isEqualByComparingTo("7500.00");
        assertThat(MriCalculator.tax(new BigDecimal("100000"), new BigDecimal("10"))).isEqualByComparingTo("10000.00");
        assertThat(MriCalculator.tax(new BigDecimal("33333.33"), new BigDecimal("7.5"))).isEqualByComparingTo("2500.00");
        assertThat(MriCalculator.tax(BigDecimal.ZERO, new BigDecimal("7.5"))).isEqualByComparingTo("0.00");
    }

    @Test
    void theRateIsNotAssumedTheSameForEveryone() {
        BigDecimal gross = new BigDecimal("250000");
        assertThat(MriCalculator.tax(gross, new BigDecimal("7.5")))
                .isNotEqualByComparingTo(MriCalculator.tax(gross, new BigDecimal("10")));
    }

    @Test
    void nonsenseInputsAreRefused() {
        assertThatThrownBy(() -> MriCalculator.tax(new BigDecimal("-1"), BigDecimal.ONE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MriCalculator.tax(BigDecimal.ONE, new BigDecimal("101"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MriCalculator.tax(null, BigDecimal.ONE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theReturnIsDueOnThe20thOfTheFollowingMonth() {
        assertThat(MriCalculator.dueDate(YearMonth.of(2026, 9))).isEqualTo(LocalDate.of(2026, 10, 20));
        assertThat(MriCalculator.dueDate(YearMonth.of(2026, 12))).isEqualTo(LocalDate.of(2027, 1, 20));
    }

    @Test
    void daysUntilDueGoesNegativeOnceItHasPassed() {
        YearMonth sep = YearMonth.of(2026, 9);
        assertThat(MriCalculator.daysUntilDue(sep, LocalDate.of(2026, 10, 3))).isEqualTo(17);
        assertThat(MriCalculator.daysUntilDue(sep, LocalDate.of(2026, 10, 20))).isZero();
        assertThat(MriCalculator.daysUntilDue(sep, LocalDate.of(2026, 10, 25))).isEqualTo(-5);
    }

    @Test
    void annualisedBandCheckUsesTheConfiguredBounds() {
        BigDecimal low = new BigDecimal("288000");
        BigDecimal high = new BigDecimal("15000000");
        assertThat(MriCalculator.withinBand(new BigDecimal("24000"), low, high)).isTrue();   // exactly 288,000 a year
        assertThat(MriCalculator.withinBand(new BigDecimal("23999"), low, high)).isFalse();
        assertThat(MriCalculator.withinBand(new BigDecimal("1250000"), low, high)).isTrue(); // exactly 15,000,000
        assertThat(MriCalculator.withinBand(new BigDecimal("1250001"), low, high)).isFalse();
    }
}
