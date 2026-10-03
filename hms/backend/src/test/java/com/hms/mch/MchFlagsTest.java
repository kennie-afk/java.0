package com.hms.mch;

import static org.assertj.core.api.Assertions.assertThat;

import com.hms.mch.MchModels.AncVisitInput;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The antenatal warnings are arithmetic on the readings, so they are tested without a database. */
class MchFlagsTest {

    private static AncVisitInput visit(Integer sys, Integer dia, String hb, Integer fhr, String fundal, String presentation, String protein,
                                       String syphilis, String hiv) {
        return new AncVisitInput(null, null, sys, dia, fundal == null ? null : new BigDecimal(fundal), fhr, presentation,
                hb == null ? null : new BigDecimal(hb), hiv, syphilis, protein, null, null, null, null, null);
    }

    @Test
    void ordinaryReadingsRaiseNothing() {
        assertThat(MchService.flagsFor(visit(118, 76, "12.4", 140, "26", "CEPHALIC", "NEGATIVE", "NEGATIVE", "NEGATIVE"), 26 * 7, 27)).isEmpty();
    }

    @Test
    void bloodPressureHasAnAmberAndARedThreshold() {
        assertThat(MchService.flagsFor(visit(139, 89, null, null, null, null, null, null, null), 200, 27)).isEmpty();
        assertThat(MchService.flagsFor(visit(140, 80, null, null, null, null, null, null, null), 200, 27)).containsExactly("HYPERTENSION");
        assertThat(MchService.flagsFor(visit(120, 90, null, null, null, null, null, null, null), 200, 27)).containsExactly("HYPERTENSION");
        assertThat(MchService.flagsFor(visit(160, 100, null, null, null, null, null, null, null), 200, 27)).containsExactly("SEVERE_HYPERTENSION");
        assertThat(MchService.flagsFor(visit(130, 110, null, null, null, null, null, null, null), 200, 27)).containsExactly("SEVERE_HYPERTENSION");
    }

    @Test
    void proteinOnlyMattersWithRaisedPressure() {
        assertThat(MchService.flagsFor(visit(118, 76, null, null, null, null, "2+", null, null), 200, 27)).isEmpty();
        assertThat(MchService.flagsFor(visit(150, 95, null, null, null, null, "TRACE", null, null), 200, 27)).containsExactly("HYPERTENSION");
        assertThat(MchService.flagsFor(visit(150, 95, null, null, null, null, "2+", null, null), 200, 27)).containsExactly("HYPERTENSION", "PRE_ECLAMPSIA_SIGNS");
    }

    @Test
    void anaemiaHasTwoLevels() {
        assertThat(MchService.flagsFor(visit(null, null, "11.0", null, null, null, null, null, null), 100, 27)).isEmpty();
        assertThat(MchService.flagsFor(visit(null, null, "10.9", null, null, null, null, null, null), 100, 27)).containsExactly("ANAEMIA");
        assertThat(MchService.flagsFor(visit(null, null, "6.9", null, null, null, null, null, null), 100, 27)).containsExactly("SEVERE_ANAEMIA");
    }

    @Test
    void fetalHeartRateIsOnlyJudgedFromTwentyWeeks() {
        assertThat(MchService.flagsFor(visit(null, null, null, 100, null, null, null, null, null), 139, 27)).isEmpty();
        assertThat(MchService.flagsFor(visit(null, null, null, 100, null, null, null, null, null), 140, 27)).containsExactly("FETAL_HEART_RATE");
        assertThat(MchService.flagsFor(visit(null, null, null, 165, null, null, null, null, null), 200, 27)).containsExactly("FETAL_HEART_RATE");
        assertThat(MchService.flagsFor(visit(null, null, null, 110, null, null, null, null, null), 200, 27)).isEmpty();
    }

    @Test
    void fundalHeightIsComparedWithWeeksInTheMiddleOfPregnancy() {
        // 28 weeks: 25 cm is 3 short (fine), 24 cm is 4 short (flagged).
        assertThat(MchService.flagsFor(visit(null, null, null, null, "25", null, null, null, null), 196, 27)).isEmpty();
        assertThat(MchService.flagsFor(visit(null, null, null, null, "24", null, null, null, null), 196, 27)).containsExactly("FUNDAL_HEIGHT");
        // Outside 20-34 weeks the rule of thumb does not apply.
        assertThat(MchService.flagsFor(visit(null, null, null, null, "10", null, null, null, null), 100, 27)).isEmpty();
    }

    @Test
    void presentationMattersFromThirtySixWeeks() {
        assertThat(MchService.flagsFor(visit(null, null, null, null, null, "BREECH", null, null, null), 251, 27)).isEmpty();
        assertThat(MchService.flagsFor(visit(null, null, null, null, null, "BREECH", null, null, null), 252, 27)).containsExactly("MALPRESENTATION");
        assertThat(MchService.flagsFor(visit(null, null, null, null, null, "CEPHALIC", null, null, null), 260, 27)).isEmpty();
    }

    @Test
    void infectionsAndAgeAreFlaggedForFollowUp() {
        assertThat(MchService.flagsFor(visit(null, null, null, null, null, null, null, "REACTIVE", "POSITIVE"), 100, 27))
                .containsExactly("SYPHILIS_REACTIVE", "HIV_NEW_POSITIVE");
        assertThat(MchService.flagsFor(visit(null, null, null, null, null, null, null, null, "KNOWN_POSITIVE"), 100, 27)).isEmpty();
        assertThat(MchService.flagsFor(visit(null, null, null, null, null, null, null, null, null), 100, 17)).containsExactly("ADOLESCENT");
        assertThat(MchService.flagsFor(visit(null, null, null, null, null, null, null, null, null), 100, 35)).containsExactly("ADVANCED_MATERNAL_AGE");
        assertThat(MchService.flagsFor(visit(null, null, null, null, null, null, null, null, null), 100, 18)).isEmpty();
    }

    @Test
    void theScheduleHasNoDuplicatesAndEverySeriesChainResolves() {
        List<String> codes = ImmunisationSchedule.DOSES.stream().map(ImmunisationSchedule.Dose::code).toList();
        assertThat(codes).doesNotHaveDuplicates();
        for (var d : ImmunisationSchedule.DOSES) {
            if (d.previous() != null) {
                assertThat(codes).contains(d.previous());
            }
        }
        java.time.LocalDate birth = java.time.LocalDate.of(2026, 1, 31);
        assertThat(ImmunisationSchedule.find("penta_1").orElseThrow().dueOn(birth)).isEqualTo(birth.plusWeeks(6));
        assertThat(ImmunisationSchedule.find("MR_1").orElseThrow().dueOn(birth)).isEqualTo(java.time.LocalDate.of(2026, 10, 31));
        assertThat(ImmunisationSchedule.find("MR_2").orElseThrow().dueOn(birth)).isEqualTo(java.time.LocalDate.of(2027, 7, 31));
    }
}
