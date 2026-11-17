package com.smartseason.weather.cropmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartseason.weather.cropmodel.SeasonForecaster.Forecast;
import com.smartseason.weather.cropmodel.SeasonForecaster.Inputs;
import com.smartseason.weather.cropmodel.SeasonForecaster.Member;
import com.smartseason.weather.cropmodel.SeasonForecaster.Near;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class SeasonForecasterTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 1);
    private static final CropParams BEANS = CropParams.find("BEANS").orElseThrow();
    private static final SoilProfile LOAM = SoilProfile.of("LOAM", SoilProfile.Source.USER);

    private static double[] fill(int n, double v) {
        double[] a = new double[n];
        Arrays.fill(a, v);
        return a;
    }

    private static History history(double rainPerDay) {
        int days = 25 * 366;
        LocalDate start = LocalDate.of(2001, 1, 1);
        return new History(start, fill(days, rainPerDay), fill(days, 4), fill(days, 25), fill(days, 13));
    }

    private static Near near(double rain) {
        return new Near(fill(23, rain), fill(23, 4), fill(23, 25), fill(23, 13));
    }

    /** Members that are dry until a given day, then wet. */
    private static List<Member> members(int count, int dryUntil, double wet) {
        List<Member> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double[] rain = new double[217];
            for (int d = 0; d < rain.length; d++) {
                rain[d] = d < dryUntil ? 0 : wet;
            }
            out.add(new Member(rain, fill(217, 25), fill(217, 13)));
        }
        return out;
    }

    @Test
    void wetWeatherGivesAHighYieldAndDryWeatherALowOne() {
        Forecast wet = SeasonForecaster.forecast(BEANS, LOAM,
                new Inputs(TODAY, -0.3, near(6), members(50, 0, 6), history(6)), TODAY, 2000);
        Forecast dry = SeasonForecaster.forecast(BEANS, LOAM,
                new Inputs(TODAY, -0.3, near(0), members(50, 217, 0), history(0.3)), TODAY, 2000);
        assertEquals(100.0, wet.yieldPctOfAttainable().p50(), 0.1);
        assertTrue(dry.yieldPctOfAttainable().p50() < 30, "dry " + dry.yieldPctOfAttainable());
        assertTrue(wet.yieldKgPerHa().p50() > dry.yieldKgPerHa().p50());
        assertEquals(2000.0, wet.yieldKgPerHa().p50(), 0.1);
    }

    @Test
    void theSpreadWidensWhenFuturesDisagree() {
        // Half the seasonal members are drought, half are wet: P10 must sit well below P90.
        List<Member> mixed = new ArrayList<>(members(25, 217, 0));
        mixed.addAll(members(25, 0, 6));
        Forecast f = SeasonForecaster.forecast(BEANS, LOAM,
                new Inputs(TODAY, -0.3, near(3), mixed, history(3)), TODAY, 2000);
        assertTrue(f.yieldPctOfAttainable().p90() - f.yieldPctOfAttainable().p10() > 20,
                "range " + f.yieldPctOfAttainable());
        assertTrue(f.chanceBelow60Pct() > 5);
    }

    @Test
    void worksFromHistoryAloneAndCountsTheScenarios() {
        Forecast f = SeasonForecaster.forecast(BEANS, LOAM,
                new Inputs(TODAY, -0.3, near(5), List.of(), history(5)), TODAY, 2000);
        assertEquals(0, f.ensembleScenarios());
        assertEquals(25, f.historyScenarios());
        assertEquals(100.0, f.yieldPctOfAttainable().p50(), 0.1);
    }

    @Test
    void plantingAfterTheRainsBeginsIsRankedBetterThanPlantingNow() {
        // Bone dry for the first 80 days then wet: waiting should beat planting today.
        Forecast f = SeasonForecaster.forecast(BEANS, LOAM,
                new Inputs(TODAY, -0.3, near(0), members(50, 80, 6), history(1.5)), TODAY, 2000);
        double now = f.alternatives().getFirst().yieldPctP50();
        double best = f.alternatives().stream().mapToDouble(a -> a.yieldPctP50()).max().orElse(0);
        assertTrue(best > now + 20, "now " + now + " best " + best);
        assertEquals(TODAY, f.alternatives().getFirst().plantingDate());
    }

    @Test
    void phasesRunInOrderAndEndAtHarvest() {
        Forecast f = SeasonForecaster.forecast(BEANS, LOAM,
                new Inputs(TODAY, -0.3, near(5), members(50, 0, 5), history(5)), TODAY, 2000);
        assertEquals(4, f.phases().size());
        for (int i = 1; i < 4; i++) {
            assertTrue(!f.phases().get(i).start().isBefore(f.phases().get(i - 1).start()));
        }
        assertEquals(f.harvest().p50(), f.phases().get(3).end());
        assertTrue(f.harvest().p10().compareTo(f.harvest().p90()) <= 0);
    }

    @Test
    void aPlantingDateOutsideTheWindowIsRejected() {
        Inputs in = new Inputs(TODAY, -0.3, near(5), List.of(), history(5));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> SeasonForecaster.forecast(BEANS, LOAM, in, TODAY.plusDays(400), 2000));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> SeasonForecaster.forecast(BEANS, LOAM, in, TODAY.minusDays(30), 2000));
    }

    @Test
    void weightedPercentilesFollowTheWeights() {
        double[] v = {1, 2, 3, 4};
        assertEquals(2, SeasonForecaster.weightedPercentile(v, new double[] {.25, .25, .25, .25}, 50));
        assertEquals(4, SeasonForecaster.weightedPercentile(v, new double[] {.05, .05, .05, .85}, 50));
    }
}
