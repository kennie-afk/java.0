package com.smartseason.weather.cropmodel;

import java.time.DateTimeException;
import java.time.LocalDate;

/**
 * Daily observed weather for many past years, held in arrays from {@code start}. Gaps (NaN) are
 * filled with the average for that calendar day, so an analog year is never left with a hole.
 */
public final class History {

    private final LocalDate start;
    private final double[] rain;
    private final double[] et0;
    private final double[] tmax;
    private final double[] tmin;
    private final double[][] climo; // [variable][month * 31 + day - 1]
    private Double scale;

    public History(LocalDate start, double[] rain, double[] et0, double[] tmax, double[] tmin) {
        this.start = start;
        this.rain = rain;
        this.et0 = et0;
        this.tmax = tmax;
        this.tmin = tmin;
        this.climo = new double[][] {climo(rain), climo(et0), climo(tmax), climo(tmin)};
    }

    public LocalDate start() {
        return start;
    }

    public LocalDate end() {
        return start.plusDays(rain.length - 1L);
    }

    public int days() {
        return rain.length;
    }

    public double rain(LocalDate d) {
        return value(rain, 0, d);
    }

    public double et0(LocalDate d) {
        return value(et0, 1, d);
    }

    public double tmax(LocalDate d) {
        return value(tmax, 2, d);
    }

    public double tmin(LocalDate d) {
        return value(tmin, 3, d);
    }

    /** The same calendar day in another year; 29 February falls back to the 28th. */
    public static LocalDate sameDayIn(LocalDate d, int year) {
        try {
            return LocalDate.of(year, d.getMonth(), d.getDayOfMonth());
        } catch (DateTimeException leap) {
            return LocalDate.of(year, d.getMonth(), 28);
        }
    }

    /**
     * How much Penman-Monteith evaporation (Open-Meteo) exceeds Hargreaves at this latitude,
     * over the whole record. Applied to Hargreaves wherever only temperature is available.
     */
    public synchronized double hargreavesScale(double lat) {
        if (scale == null) {
            double reference = 0;
            double estimate = 0;
            for (int i = 0; i < rain.length; i++) {
                if (Double.isNaN(et0[i]) || Double.isNaN(tmax[i]) || Double.isNaN(tmin[i])) {
                    continue;
                }
                reference += et0[i];
                estimate += Evaporation.hargreaves(lat, start.plusDays(i), tmax[i], tmin[i]);
            }
            double ratio = estimate > 0 ? reference / estimate : 1;
            scale = Math.max(0.6, Math.min(1.6, ratio));
        }
        return scale;
    }

    private double value(double[] series, int variable, LocalDate d) {
        long i = java.time.temporal.ChronoUnit.DAYS.between(start, d);
        if (i >= 0 && i < series.length && !Double.isNaN(series[(int) i])) {
            return series[(int) i];
        }
        return climo[variable][(d.getMonthValue() - 1) * 31 + d.getDayOfMonth() - 1];
    }

    private double[] climo(double[] series) {
        double[] sum = new double[12 * 31];
        int[] count = new int[12 * 31];
        for (int i = 0; i < series.length; i++) {
            if (Double.isNaN(series[i])) {
                continue;
            }
            LocalDate d = start.plusDays(i);
            int slot = (d.getMonthValue() - 1) * 31 + d.getDayOfMonth() - 1;
            sum[slot] += series[i];
            count[slot]++;
        }
        double[] mean = new double[12 * 31];
        for (int s = 0; s < mean.length; s++) {
            mean[s] = count[s] == 0 ? 0 : sum[s] / count[s];
        }
        return mean;
    }
}
