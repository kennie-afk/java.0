package com.smartseason.weather.cropmodel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.smartseason.weather.cropmodel.CropModel.Outcome;
import com.smartseason.weather.cropmodel.CropModel.Weather;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class CropModelTest {

    private static final CropParams MAIZE = CropParams.find("MAIZE").orElseThrow();
    private static final int DAYS = 700;

    /** Constant weather: a warm day with a fixed rain and evaporation every day. */
    private static Weather constant(double rain, double et0, double tmax, double tmin) {
        double[] r = new double[DAYS];
        double[] e = new double[DAYS];
        double[] hi = new double[DAYS];
        double[] lo = new double[DAYS];
        Arrays.fill(r, rain);
        Arrays.fill(e, et0);
        Arrays.fill(hi, tmax);
        Arrays.fill(lo, tmin);
        return new Weather(r, e, hi, lo);
    }

    @Test
    void plentyOfRainGivesTheFullAttainableYield() {
        Outcome o = CropModel.simulate(MAIZE, SoilProfile.of("LOAM", SoilProfile.Source.USER),
                constant(6, 4, 26, 14), 7);
        assertEquals(1.0, o.ratio(), 1e-9);
        assertTrue(o.harvestIndex() > 7);
    }

    @Test
    void noRainCutsYieldHard() {
        Outcome o = CropModel.simulate(MAIZE, SoilProfile.of("LOAM", SoilProfile.Source.USER),
                constant(0, 4, 26, 14), 7);
        assertTrue(o.ratio() < 0.15, "drought should wipe out most of the yield, got " + o.ratio());
        assertTrue(o.waterUse() < 0.4);
    }

    @Test
    void aDrySpellAtFloweringCostsMoreThanOneAtRipening() {
        Weather base = constant(5, 4, 26, 14);
        Outcome clean = CropModel.simulate(MAIZE, SoilProfile.of("LOAM", SoilProfile.Source.USER), base, 7);
        int[] s = clean.phaseStart();
        int flowering = s[2];
        int ripening = s[3];

        Outcome dryFlowering = CropModel.simulate(MAIZE, SoilProfile.of("LOAM", SoilProfile.Source.USER),
                dry(base, flowering, 35), 7);
        Outcome dryRipening = CropModel.simulate(MAIZE, SoilProfile.of("LOAM", SoilProfile.Source.USER),
                dry(base, ripening, 35), 7);
        assertTrue(dryFlowering.ratio() < dryRipening.ratio(),
                "flowering " + dryFlowering.ratio() + " vs ripening " + dryRipening.ratio());
        assertTrue(dryFlowering.ratio() < 0.9);
    }

    private static Weather dry(Weather w, int from, int days) {
        double[] rain = w.rain().clone();
        for (int i = from; i < from + days; i++) {
            rain[i] = 0;
        }
        return new Weather(rain, w.et0(), w.tmax(), w.tmin());
    }

    @Test
    void coolerWeatherDelaysMaturity() {
        SoilProfile soil = SoilProfile.of("LOAM", SoilProfile.Source.USER);
        Outcome warm = CropModel.simulate(MAIZE, soil, constant(5, 4, 30, 18), 7);
        Outcome cool = CropModel.simulate(MAIZE, soil, constant(5, 3, 22, 10), 7);
        assertTrue(cool.harvestIndex() > warm.harvestIndex() + 40,
                "warm " + warm.harvestIndex() + " cool " + cool.harvestIndex());
    }

    @Test
    void deeperWaterReserveRidesOutADrySpellBetter() {
        Weather w = constant(4, 4, 26, 14);
        int plant = 7;
        Outcome probe = CropModel.simulate(MAIZE, SoilProfile.of("LOAM", SoilProfile.Source.USER), w, plant);
        Weather spell = dry(w, probe.phaseStart()[2] - 5, 30);
        Outcome sand = CropModel.simulate(MAIZE, SoilProfile.of("SAND", SoilProfile.Source.USER), spell, plant);
        Outcome loam = CropModel.simulate(MAIZE, SoilProfile.of("LOAM", SoilProfile.Source.USER), spell, plant);
        assertTrue(loam.ratio() > sand.ratio(), "loam " + loam.ratio() + " sand " + sand.ratio());
    }

    @Test
    void aCropThatCannotFinishInTheSeriesIsReportedAsNotMature() {
        Outcome o = CropModel.simulate(MAIZE, SoilProfile.assumed(), constant(5, 4, 12, 6), 7);
        assertEquals(-1, o.harvestIndex()); // below the 10 C base: no heat accumulates
    }

    @Test
    void heatAccumulatesOnlyBetweenTheBaseAndTheCeiling() {
        assertEquals(0, MAIZE.gdd(9, 5), 1e-9);
        assertEquals(10, MAIZE.gdd(30, 10), 1e-9); // (30+10)/2 - 10
        assertEquals(MAIZE.gdd(30, 18), MAIZE.gdd(40, 18), 1e-9); // heat above 30 C is capped
    }

    @Test
    void earlyVarietiesMatureSoonerThanLateOnes() {
        SoilProfile soil = SoilProfile.of("LOAM", SoilProfile.Source.USER);
        Weather w = constant(5, 4, 28, 16);
        int early = CropModel.simulate(MAIZE.withMaturityFactor(CropParams.maturityFactor("early").orElseThrow()),
                soil, w, 7).harvestIndex();
        int late = CropModel.simulate(MAIZE.withMaturityFactor(CropParams.maturityFactor("LATE").orElseThrow()),
                soil, w, 7).harvestIndex();
        assertTrue(late - early > 40, "early " + early + " late " + late);
        assertTrue(CropParams.maturityFactor("slow").isEmpty());
    }
}
