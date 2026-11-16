package com.smartseason.weather.climate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PlantingAdvisorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 3, 1);

    /** Every day of the previous 25 years gets the same rain, so the history is predictable. */
    private static Map<LocalDate, Double> history(double mmPerDay) {
        Map<LocalDate, Double> map = new HashMap<>();
        for (LocalDate d = LocalDate.of(2000, 1, 1); d.isBefore(TODAY); d = d.plusDays(1)) {
            map.put(d, mmPerDay);
        }
        return map;
    }

    private static List<double[]> members(double mmPerDay, int count) {
        List<double[]> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double[] series = new double[183];
            Arrays.fill(series, mmPerDay);
            list.add(series);
        }
        return list;
    }

    private static double[] dry16() {
        return new double[16];
    }

    @Test
    void wetSeasonSuitsMaizeAndDrySeasonDoesNot() {
        // 5 mm/day: maize (150 d) gets 750 mm against a 500-800 mm need.
        PlantingAdvisor.Result wet = PlantingAdvisor.advise(
                TODAY, new double[7], dry16(), members(5, 50), history(5));
        PlantingAdvisor.CropAdvice maize = wet.crops().stream()
                .filter(c -> c.code().equals("MAIZE")).findFirst().orElseThrow();
        assertEquals("SUITABLE", maize.verdict());
        assertEquals(750.0, maize.expectedMm(), 0.5);

        // 1.5 mm/day: maize gets 225 mm, far short; nothing is suitable.
        PlantingAdvisor.Result dry = PlantingAdvisor.advise(
                TODAY, new double[7], dry16(), members(1.5, 50), history(1.5));
        assertTrue(dry.crops().stream().allMatch(c -> c.verdict().equals("NOT_SUITABLE")));
        assertTrue(dry.headline().contains("irrigation"));
    }

    @Test
    void shorterCropsFitDrierSeasons() {
        // 3.6 mm/day: beans (90 d) get 324 mm and clear their 300 mm need; maize (150 d) gets 540.
        PlantingAdvisor.Result r = PlantingAdvisor.advise(
                TODAY, new double[7], dry16(), members(3.6, 50), history(3.6));
        PlantingAdvisor.CropAdvice beans = r.crops().stream()
                .filter(c -> c.code().equals("BEANS")).findFirst().orElseThrow();
        assertEquals("SUITABLE", beans.verdict());
        // The best-ranked crop is a suitable one.
        assertEquals("SUITABLE", r.crops().getFirst().verdict());
    }

    @Test
    void plantingRainIsDetectedAndDatedFromTheForecast() {
        double[] next = new double[16];
        next[2] = 12;
        next[3] = 10;
        next[4] = 6;           // 28 mm over days 2-4
        Arrays.fill(next, 5, 16, 2.0); // and steady light rain after
        PlantingAdvisor.Timing t = PlantingAdvisor.timing(TODAY, next);
        assertEquals("PLANT_NOW", t.status());
        assertEquals(TODAY.plusDays(1), t.plantFrom()); // window days 1-3 already holds 22 mm
    }

    @Test
    void aBurstFollowedByADrySpellIsNotPlantingRain() {
        double[] next = new double[16];
        next[0] = 25; // one big storm, then nothing
        PlantingAdvisor.Timing t = PlantingAdvisor.timing(TODAY, next);
        assertEquals("WAIT", t.status());
        assertNull(t.plantFrom());
    }

    @Test
    void lateRainIsReportedAsSoon() {
        double[] next = new double[16];
        next[7] = 15;
        next[8] = 10;
        Arrays.fill(next, 9, 16, 2.0);
        PlantingAdvisor.Timing t = PlantingAdvisor.timing(TODAY, next);
        assertEquals("PLANT_SOON", t.status());
        assertEquals(TODAY.plusDays(6), t.plantFrom()); // window days 6-8 holds 25 mm
    }

    @Test
    void worksFromHistoryAloneWhenTheSeasonalModelIsDown() {
        PlantingAdvisor.Result r = PlantingAdvisor.advise(
                TODAY, new double[7], dry16(), List.of(), history(5));
        PlantingAdvisor.CropAdvice maize = r.crops().stream()
                .filter(c -> c.code().equals("MAIZE")).findFirst().orElseThrow();
        assertNull(maize.forecastMedianMm());
        assertEquals("SUITABLE", maize.verdict());
        assertTrue(r.caveats().stream().anyMatch(c -> c.contains("history alone")));
    }

    @Test
    void anyOneDryYearDowngradesSuitableToMarginal() {
        // Median is plentiful but one year in ten is a drought: the verdict must say so.
        Map<LocalDate, Double> h = history(5);
        for (int year = 2001; year <= 2025; year += 3) { // about a third of years are dry
            for (LocalDate d = LocalDate.of(year, 3, 1); d.isBefore(LocalDate.of(year, 8, 1)); d = d.plusDays(1)) {
                h.put(d, 0.5);
            }
        }
        PlantingAdvisor.Result r = PlantingAdvisor.advise(
                TODAY, new double[7], dry16(), members(5, 50), h);
        PlantingAdvisor.CropAdvice maize = r.crops().stream()
                .filter(c -> c.code().equals("MAIZE")).findFirst().orElseThrow();
        assertEquals("MARGINAL", maize.verdict());
    }

    @Test
    void findsTheBestPlantingDateWhenTheRainComesLater() {
        // Bone dry for the first 90 days, then 6 mm/day: beans planted today are short of water,
        // but planting after the rains start puts the whole season inside them.
        List<double[]> late = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            double[] series = new double[183];
            for (int d = 0; d < 183; d++) {
                series[d] = d < 90 ? 0 : 6;
            }
            late.add(series);
        }
        PlantingAdvisor.Result r = PlantingAdvisor.advise(TODAY, new double[7], dry16(), late, history(3.5));
        PlantingAdvisor.CropAdvice beans = r.crops().stream()
                .filter(c -> c.code().equals("BEANS")).findFirst().orElseThrow();
        assertEquals("SUITABLE", beans.best().verdict());
        assertTrue(beans.best().start().isAfter(TODAY.plusDays(27)));
    }
}
