package com.smartseason.weather.cropmodel;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Crop constants for the water-balance model.
 *
 * Sources: crop coefficients (Kc), rooting depth and the depletion fraction (p) are from FAO
 * Irrigation and Drainage Paper 56 (tables 12, 22); yield response factors (Ky) are from FAO
 * Paper 33. Heat requirements are typical for the crop and vary by variety, so they are the
 * first thing to calibrate once real seasons are recorded. The figures are for a medium-maturing
 * variety; early and late varieties need 20% less and 20% more heat. The attainable yield is the
 * water-unlimited yield under good management, and a caller who knows better (a farmer's own
 * best harvest) overrides it.
 *
 * A season is four phases: establishment, development, mid-season (flowering and yield
 * formation, when water shortage costs most) and late season (ripening). Fractions are shares
 * of the heat needed to reach maturity.
 */
public record CropParams(
        String code, String name,
        double kcIni, double kcMid, double kcEnd,
        double[] phaseFraction,     // establishment, development, mid, late: sums to 1
        double[] ky,                // yield response factor in each phase
        double rootMaxM, double depletionFraction,
        double baseTempC, double gddToMaturity,
        double attainableKgPerHa) {

    public static final List<String> PHASES = List.of(
            "Establishment", "Vegetative growth", "Flowering and yield formation", "Ripening");

    public static final List<CropParams> ALL = List.of(
            new CropParams("MAIZE", "Maize", 0.30, 1.20, 0.35,
                    new double[] {0.15, 0.27, 0.33, 0.25}, new double[] {0.40, 0.50, 1.30, 0.30},
                    1.0, 0.55, 10, 1500, 6000),
            new CropParams("BEANS", "Beans", 0.40, 1.15, 0.35,
                    new double[] {0.15, 0.30, 0.30, 0.25}, new double[] {0.20, 0.50, 1.10, 0.40},
                    0.7, 0.45, 10, 950, 2000),
            new CropParams("POTATO", "Potato", 0.50, 1.15, 0.75,
                    new double[] {0.15, 0.25, 0.35, 0.25}, new double[] {0.45, 0.45, 0.80, 0.30},
                    0.6, 0.35, 4, 1250, 22000),
            new CropParams("TOMATO", "Tomato", 0.60, 1.15, 0.80,
                    new double[] {0.15, 0.25, 0.35, 0.25}, new double[] {0.40, 0.50, 1.10, 0.60},
                    0.8, 0.40, 10, 1450, 40000),
            new CropParams("SORGHUM", "Sorghum", 0.30, 1.05, 0.55,
                    new double[] {0.15, 0.25, 0.35, 0.25}, new double[] {0.20, 0.20, 0.55, 0.45},
                    1.2, 0.55, 10, 1500, 3500));

    /** Share of the medium heat requirement a variety of this maturity class needs. */
    public static Optional<Double> maturityFactor(String maturity) {
        if (maturity == null || maturity.isBlank()) {
            return Optional.of(1.0);
        }
        return switch (maturity.trim().toUpperCase(Locale.ROOT)) {
            case "EARLY" -> Optional.of(0.8);
            case "MEDIUM" -> Optional.of(1.0);
            case "LATE" -> Optional.of(1.2);
            default -> Optional.empty();
        };
    }

    public CropParams withMaturityFactor(double factor) {
        return new CropParams(code, name, kcIni, kcMid, kcEnd, phaseFraction, ky, rootMaxM,
                depletionFraction, baseTempC, gddToMaturity * factor, attainableKgPerHa);
    }

    /** Heat above which the crop stops developing faster. */
    public static final double CEILING_TEMP_C = 30;

    public static Optional<CropParams> find(String code) {
        if (code == null) {
            return Optional.empty();
        }
        String wanted = code.trim().toUpperCase(Locale.ROOT);
        return ALL.stream().filter(c -> c.code().equals(wanted)).findFirst();
    }

    /** Heat gained in one day, in degree-days. */
    public double gdd(double tmax, double tmin) {
        double hi = Math.min(tmax, CEILING_TEMP_C);
        double lo = Math.max(tmin, baseTempC);
        return Math.max(0, (hi + lo) / 2 - baseTempC);
    }
}
