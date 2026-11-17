package com.smartseason.weather.cropmodel;

import java.time.LocalDate;

/**
 * Reference evapotranspiration from temperature alone (Hargreaves-Samani, FAO-56 section 3.4).
 *
 * The seasonal ensemble supplies temperature but not the wind and humidity the full
 * Penman-Monteith needs, so this fills the gap. It is scaled to Open-Meteo's own FAO
 * Penman-Monteith figure over the location's history, which removes most of its local bias.
 */
public final class Evaporation {

    private static final double GSC = 0.0820; // solar constant, MJ m-2 min-1

    private Evaporation() {
    }

    /** Radiation at the top of the atmosphere, MJ m-2 day-1 (FAO-56 equation 21). */
    static double extraterrestrialRadiation(double latDeg, int dayOfYear) {
        double phi = Math.toRadians(latDeg);
        double dr = 1 + 0.033 * Math.cos(2 * Math.PI / 365 * dayOfYear);
        double delta = 0.409 * Math.sin(2 * Math.PI / 365 * dayOfYear - 1.39);
        double ws = Math.acos(Math.max(-1, Math.min(1, -Math.tan(phi) * Math.tan(delta))));
        return 24 * 60 / Math.PI * GSC * dr
                * (ws * Math.sin(phi) * Math.sin(delta) + Math.cos(phi) * Math.cos(delta) * Math.sin(ws));
    }

    /** Millimetres per day. */
    public static double hargreaves(double latDeg, LocalDate day, double tmax, double tmin) {
        double ra = extraterrestrialRadiation(latDeg, day.getDayOfYear());
        double range = Math.max(0, tmax - tmin);
        double tmean = (tmax + tmin) / 2;
        return Math.max(0, 0.0023 * (tmean + 17.8) * Math.sqrt(range) * ra / 2.45);
    }
}
