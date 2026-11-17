package com.smartseason.weather.cropmodel;

/**
 * One crop through one weather scenario: a daily soil-water balance (FAO-56), growth stages
 * driven by accumulated heat, and relative yield from the water the crop actually got
 * compared with what it wanted (FAO-33).
 *
 * Deterministic and free of tuning knobs that are not in the published tables. It models
 * water and heat only; nutrients, pests, frost, hail, waterlogging, runoff on slopes and the
 * variety are outside it, and the caller is told so.
 */
public final class CropModel {

    /** Daily weather for one possible future. Index 0 is the first day of the spin-up. */
    public record Weather(double[] rain, double[] et0, double[] tmax, double[] tmin) {
        public int days() {
            return rain.length;
        }
    }

    /**
     * @param ratio        water-limited yield as a share of the attainable yield, 0-1
     * @param harvestIndex day the crop reaches maturity, or -1 if the weather ran out first
     * @param phaseStart   first day of each of the four phases (same indexing)
     * @param seasonRainMm rain between planting and maturity
     * @param waterUse     actual evapotranspiration as a share of what the crop wanted
     */
    public record Outcome(double ratio, int harvestIndex, int[] phaseStart, double seasonRainMm,
                          double waterUse) {
    }

    /** Soil starts half full: nothing measures it, and the spin-up days correct it. */
    static final double INITIAL_FILL = 0.5;
    private static final double FALLOW_KC = 0.30;
    private static final double FALLOW_ROOT_M = 0.30;
    private static final double FALLOW_DEPLETION = 0.50;
    private static final double ROOT_MIN_M = 0.25;

    private CropModel() {
    }

    public static Outcome simulate(CropParams crop, SoilProfile soil, Weather w, int plantIndex) {
        double store = soil.availableWaterMmPerM() * FALLOW_ROOT_M * INITIAL_FILL;
        double taw = soil.availableWaterMmPerM() * FALLOW_ROOT_M;
        int n = w.days();

        // Fallow, from the start of the series to the planting day.
        for (int t = 0; t < Math.min(plantIndex, n); t++) {
            double[] next = step(store, taw, soil.availableWaterMmPerM() * FALLOW_ROOT_M,
                    FALLOW_DEPLETION, FALLOW_KC * w.et0()[t], w.rain()[t]);
            store = next[0];
        }

        double[] etm = new double[4];
        double[] eta = new double[4];
        int[] start = {-1, -1, -1, -1};
        double cumGdd = 0;
        double seasonRain = 0;
        double[] edge = {crop.phaseFraction()[0],
                crop.phaseFraction()[0] + crop.phaseFraction()[1],
                crop.phaseFraction()[0] + crop.phaseFraction()[1] + crop.phaseFraction()[2], 1.0};

        for (int t = plantIndex; t < n; t++) {
            double frac = cumGdd / crop.gddToMaturity();
            if (frac >= 1) {
                return outcome(crop, etm, eta, start, t, seasonRain);
            }
            int phase = frac < edge[0] ? 0 : frac < edge[1] ? 1 : frac < edge[2] ? 2 : 3;
            if (start[phase] < 0) {
                start[phase] = t;
            }
            double kc = kc(crop, frac, edge, phase);
            double rootM = ROOT_MIN_M + (crop.rootMaxM() - ROOT_MIN_M) * Math.min(1, frac / edge[1]);
            double newTaw = soil.availableWaterMmPerM() * rootM;
            // The soil below the old root zone is as full as the part above it was.
            store = taw == 0 ? 0 : store / taw * newTaw;
            taw = newTaw;

            double etc = kc * w.et0()[t];
            double[] next = step(store, taw, taw, crop.depletionFraction(), etc, w.rain()[t]);
            store = next[0];
            etm[phase] += etc;
            eta[phase] += next[1];
            seasonRain += w.rain()[t];
            cumGdd += crop.gdd(w.tmax()[t], w.tmin()[t]);
        }
        return new Outcome(Double.NaN, -1, start, seasonRain, Double.NaN);
    }

    /**
     * One day of the balance. Returns {water in the root zone afterwards, actual
     * evapotranspiration}. Rain fills the store and the excess drains away.
     */
    private static double[] step(double store, double taw, double tawForKs, double p,
                                 double etc, double rain) {
        store = Math.min(taw, store + rain);
        double readilyUsed = (1 - p) * tawForKs;
        double ks = store >= readilyUsed ? 1 : Math.max(0, store / readilyUsed);
        double actual = Math.min(store, ks * etc);
        return new double[] {store - actual, actual};
    }

    private static double kc(CropParams c, double frac, double[] edge, int phase) {
        return switch (phase) {
            case 0 -> c.kcIni();
            case 1 -> c.kcIni() + (c.kcMid() - c.kcIni()) * (frac - edge[0]) / (edge[1] - edge[0]);
            case 2 -> c.kcMid();
            default -> c.kcMid() + (c.kcEnd() - c.kcMid()) * (frac - edge[2]) / (edge[3] - edge[2]);
        };
    }

    private static Outcome outcome(CropParams crop, double[] etm, double[] eta, int[] start,
                                   int harvest, double seasonRain) {
        double ratio = 1;
        double wanted = 0;
        double got = 0;
        for (int i = 0; i < 4; i++) {
            wanted += etm[i];
            got += eta[i];
            if (etm[i] > 0) {
                ratio *= Math.max(0, 1 - crop.ky()[i] * (1 - eta[i] / etm[i]));
            }
        }
        return new Outcome(Math.min(1, ratio), harvest, start, seasonRain,
                wanted == 0 ? 1 : got / wanted);
    }
}
