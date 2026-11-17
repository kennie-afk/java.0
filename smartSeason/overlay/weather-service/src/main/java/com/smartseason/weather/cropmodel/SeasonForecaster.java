package com.smartseason.weather.cropmodel;

import com.smartseason.weather.cropmodel.CropModel.Outcome;
import com.smartseason.weather.cropmodel.CropModel.Weather;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Runs the crop model over every plausible future and reports the spread, not one number.
 *
 * The futures are of two kinds, weighted equally. Each seasonal-model member (about 50 runs
 * out to seven months) is one future: the real 16-day forecast first, then that member's
 * weather. Each of the last 25 years is another: the same forecast first, then what the weather
 * actually did on those calendar days. Where the seasonal model runs out, members continue with
 * a historical year. The history keeps the forecast honest where seasonal skill is weak.
 */
public final class SeasonForecaster {

    /** The 16-day forecast plus the 7 days before it. Index 0 is seven days before today. */
    public record Near(double[] rain, double[] et0, double[] tmax, double[] tmin) {
    }

    /** One member of the seasonal ensemble, daily from today. */
    public record Member(double[] rain, double[] tmax, double[] tmin) {
    }

    public record Inputs(LocalDate today, double lat, Near near, List<Member> members, History history) {
    }

    public record Range(Double p10, Double p50, Double p90) {
    }

    public record DateRange(LocalDate p10, LocalDate p50, LocalDate p90) {
    }

    public record Phase(String name, LocalDate start, LocalDate end) {
    }

    public record Alternative(LocalDate plantingDate, Double yieldPctP10, Double yieldPctP50,
                              LocalDate harvestP50) {
    }

    public record Forecast(String crop, String cropName, LocalDate plantingDate, SoilProfile soil,
                           Range yieldPctOfAttainable, Range yieldKgPerHa, Range seasonRainMm,
                           DateRange harvest, Double chanceBelow60Pct, List<Phase> phases,
                           List<Alternative> alternatives, int scenarios, int ensembleScenarios,
                           int historyScenarios, double seasonCoveredByModel) {
    }

    public static final int PAST_DAYS = 7;
    public static final int MAX_PLANT_OFFSET = 180;
    private static final int HORIZON = PAST_DAYS + MAX_PLANT_OFFSET + 430;
    private static final int HISTORY_YEARS = 25;

    private SeasonForecaster() {
    }

    public static Forecast forecast(CropParams crop, SoilProfile soil, Inputs in,
                                    LocalDate plantingDate, double attainableKgPerHa) {
        int offset = (int) ChronoUnit.DAYS.between(in.today(), plantingDate);
        if (offset < -PAST_DAYS || offset > MAX_PLANT_OFFSET) {
            throw new IllegalArgumentException("Planting date must be within the past week or "
                    + MAX_PLANT_OFFSET + " days ahead.");
        }
        List<Weather> ensemble = ensembleFutures(in);
        List<Weather> analogs = analogFutures(in);

        Runs main = run(crop, soil, ensemble, analogs, PAST_DAYS + offset);
        if (main.valid() == 0) {
            throw new IllegalStateException("The crop did not reach maturity in any scenario.");
        }
        LocalDate base = in.today().minusDays(PAST_DAYS);

        List<Alternative> alternatives = new ArrayList<>();
        for (int o = 0; o <= MAX_PLANT_OFFSET; o += 14) {
            Runs r = run(crop, soil, ensemble, analogs, PAST_DAYS + o);
            if (r.valid() == 0) {
                continue;
            }
            alternatives.add(new Alternative(in.today().plusDays(o),
                    round(r.percentile(r.ratio(), 10) * 100), round(r.percentile(r.ratio(), 50) * 100),
                    base.plusDays((long) r.percentile(asDouble(r.harvest()), 50))));
        }

        double[] ratio = main.ratio();
        double[] kg = Arrays.stream(ratio).map(r -> r * attainableKgPerHa).toArray();
        double[] hv = asDouble(main.harvest());
        List<Phase> phases = new ArrayList<>();
        int[] starts = new int[4];
        for (int p = 0; p < 4; p++) {
            starts[p] = (int) main.percentile(asDouble(main.phaseStart(p)), 50);
        }
        int end = (int) main.percentile(hv, 50);
        for (int p = 0; p < 4; p++) {
            int to = p < 3 ? Math.max(starts[p], starts[p + 1]) : end;
            phases.add(new Phase(CropParams.PHASES.get(p), base.plusDays(starts[p]), base.plusDays(to)));
        }

        int covered = 0;
        int total = 0;
        int firstDay = PAST_DAYS + offset;
        int medianEnd = end;
        for (int i = firstDay; i < medianEnd; i++) {
            total++;
            if (i - PAST_DAYS < seasonalReach(in)) {
                covered++;
            }
        }
        double chance = main.weightedShare(ratio, 0.6);
        return new Forecast(crop.code(), crop.name(), plantingDate, soil,
                new Range(round(main.percentile(ratio, 10) * 100), round(main.percentile(ratio, 50) * 100),
                        round(main.percentile(ratio, 90) * 100)),
                new Range(round(main.percentile(kg, 10)), round(main.percentile(kg, 50)),
                        round(main.percentile(kg, 90))),
                new Range(round(main.percentile(main.seasonRain(), 10)),
                        round(main.percentile(main.seasonRain(), 50)),
                        round(main.percentile(main.seasonRain(), 90))),
                new DateRange(base.plusDays((long) main.percentile(hv, 10)),
                        base.plusDays((long) main.percentile(hv, 50)),
                        base.plusDays((long) main.percentile(hv, 90))),
                round(chance * 100), phases, alternatives, main.valid(),
                main.ensembleValid(), main.valid() - main.ensembleValid(),
                total == 0 ? 0 : Math.round(100.0 * covered / total) / 100.0);
    }

    /** Days from today that the seasonal model actually covers (the forecast alone if it is down). */
    private static int seasonalReach(Inputs in) {
        return in.members().isEmpty() ? 16 : in.members().stream()
                .mapToInt(m -> m.rain().length).min().orElse(16);
    }

    // ---- scenarios --------------------------------------------------------------------

    static List<Weather> ensembleFutures(Inputs in) {
        List<Weather> out = new ArrayList<>();
        int number = 0;
        for (Member m : in.members()) {
            out.add(build(in, m, number++));
        }
        return out;
    }

    static List<Weather> analogFutures(Inputs in) {
        List<Weather> out = new ArrayList<>();
        for (int j = 0; j < HISTORY_YEARS; j++) {
            out.add(build(in, null, j));
        }
        return out;
    }

    /** The near forecast, then the member (or a historical year), then history to the horizon. */
    private static Weather build(Inputs in, Member member, int number) {
        double[] rain = new double[HORIZON];
        double[] et0 = new double[HORIZON];
        double[] tmax = new double[HORIZON];
        double[] tmin = new double[HORIZON];
        LocalDate base = in.today().minusDays(PAST_DAYS);
        int nearDays = in.near().rain().length;
        int endYear = in.history().end().getYear();
        int analogYear = endYear - (number % HISTORY_YEARS);
        double scale = in.history().hargreavesScale(in.lat());

        for (int i = 0; i < HORIZON; i++) {
            LocalDate d = base.plusDays(i);
            int k = i - PAST_DAYS;
            if (i < nearDays) {
                rain[i] = in.near().rain()[i];
                et0[i] = in.near().et0()[i];
                tmax[i] = in.near().tmax()[i];
                tmin[i] = in.near().tmin()[i];
            } else if (member != null && k < member.rain().length) {
                rain[i] = member.rain()[k];
                tmax[i] = member.tmax()[k];
                tmin[i] = member.tmin()[k];
                et0[i] = scale * Evaporation.hargreaves(in.lat(), d, tmax[i], tmin[i]);
            } else {
                LocalDate analog = History.sameDayIn(d,
                        analogYear + (d.getYear() - in.today().getYear()));
                rain[i] = in.history().rain(analog);
                et0[i] = in.history().et0(analog);
                tmax[i] = in.history().tmax(analog);
                tmin[i] = in.history().tmin(analog);
            }
        }
        return new Weather(rain, et0, tmax, tmin);
    }

    // ---- statistics -------------------------------------------------------------------

    /** Outcomes over all futures, with a weight for each so the two kinds count equally. */
    private record Runs(List<Outcome> outcomes, double[] weights, int ensembleValid) {

        int valid() {
            return outcomes.size();
        }

        double[] ratio() {
            return outcomes.stream().mapToDouble(Outcome::ratio).toArray();
        }

        double[] seasonRain() {
            return outcomes.stream().mapToDouble(Outcome::seasonRainMm).toArray();
        }

        int[] harvest() {
            return outcomes.stream().mapToInt(Outcome::harvestIndex).toArray();
        }

        int[] phaseStart(int p) {
            return outcomes.stream().mapToInt(o -> o.phaseStart()[p] < 0
                    ? o.harvestIndex() : o.phaseStart()[p]).toArray();
        }

        double percentile(double[] values, double p) {
            return weightedPercentile(values, weights, p);
        }

        double weightedShare(double[] values, double below) {
            double total = 0;
            double hit = 0;
            for (int i = 0; i < values.length; i++) {
                total += weights[i];
                if (values[i] < below) {
                    hit += weights[i];
                }
            }
            return total == 0 ? 0 : hit / total;
        }
    }

    private static Runs run(CropParams crop, SoilProfile soil, List<Weather> ensemble,
                            List<Weather> analogs, int plantIndex) {
        List<Outcome> outcomes = new ArrayList<>();
        List<Boolean> fromEnsemble = new ArrayList<>();
        for (Weather w : ensemble) {
            Outcome o = CropModel.simulate(crop, soil, w, plantIndex);
            if (o.harvestIndex() >= 0) {
                outcomes.add(o);
                fromEnsemble.add(true);
            }
        }
        for (Weather w : analogs) {
            Outcome o = CropModel.simulate(crop, soil, w, plantIndex);
            if (o.harvestIndex() >= 0) {
                outcomes.add(o);
                fromEnsemble.add(false);
            }
        }
        int e = (int) fromEnsemble.stream().filter(b -> b).count();
        int a = outcomes.size() - e;
        double[] weights = new double[outcomes.size()];
        for (int i = 0; i < weights.length; i++) {
            if (e == 0 || a == 0) {
                weights[i] = 1.0 / outcomes.size();
            } else {
                weights[i] = fromEnsemble.get(i) ? 0.5 / e : 0.5 / a;
            }
        }
        return new Runs(outcomes, weights, e);
    }

    static double weightedPercentile(double[] values, double[] weights, double p) {
        Integer[] order = new Integer[values.length];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        Arrays.sort(order, (x, y) -> Double.compare(values[x], values[y]));
        double total = Arrays.stream(weights).sum();
        double target = p / 100.0 * total;
        double run = 0;
        for (int idx : order) {
            run += weights[idx];
            if (run >= target - 1e-12) {
                return values[idx];
            }
        }
        return values[order[order.length - 1]];
    }

    private static double[] asDouble(int[] ints) {
        return Arrays.stream(ints).asDoubleStream().toArray();
    }

    private static Double round(double v) {
        return Double.isNaN(v) ? null : Math.round(v * 10) / 10.0;
    }
}
