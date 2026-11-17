package com.smartseason.weather.cropmodel;

import java.util.Locale;
import java.util.Optional;

/**
 * How much water a metre of soil can hold for plants (field capacity minus wilting point).
 *
 * The figures are the mid-range values in FAO Paper 56 table 19. The texture comes from
 * measured clay and sand content (ISRIC SoilGrids) or from the caller; when neither is
 * available the model assumes a loam and says so, because a wrong soil is the single biggest
 * unknown in the water balance.
 */
public record SoilProfile(String texture, double availableWaterMmPerM, Source source,
                          Double clayPct, Double sandPct) {

    public enum Source { MEASURED, USER, ASSUMED }

    public static SoilProfile of(String texture, Source source) {
        return of(texture, source, null, null);
    }

    public static SoilProfile of(String texture, Source source, Double clay, Double sand) {
        return new SoilProfile(texture, switch (texture) {
            case "SAND" -> 90;
            case "SANDY_LOAM" -> 130;
            case "CLAY" -> 150;
            case "CLAY_LOAM" -> 165;
            default -> 155; // LOAM
        }, source, clay, sand);
    }

    public static Optional<SoilProfile> fromName(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        String t = name.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        return switch (t) {
            case "SAND", "SANDY_LOAM", "LOAM", "CLAY_LOAM", "CLAY" -> Optional.of(of(t, Source.USER));
            default -> Optional.empty();
        };
    }

    /** A coarse reading of the USDA texture triangle, enough to pick one of five classes. */
    public static SoilProfile fromClaySand(double clayPct, double sandPct) {
        String texture;
        if (sandPct >= 85) {
            texture = "SAND";
        } else if (sandPct >= 70) {
            texture = "SANDY_LOAM";
        } else if (clayPct >= 40) {
            texture = "CLAY";
        } else if (clayPct >= 27 && sandPct <= 45) {
            texture = "CLAY_LOAM";
        } else {
            texture = "LOAM";
        }
        return of(texture, Source.MEASURED, clayPct, sandPct);
    }

    public static SoilProfile assumed() {
        return of("LOAM", Source.ASSUMED);
    }
}
