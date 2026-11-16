package com.smartseason.weather.climate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Turns measured and forecast rainfall into planting advice.
 *
 * There is no language model here and no hand-typed forecast. The inputs are three
 * real series for one location: the next 16 days of forecast rain, the seasonal
 * ensemble (about 50 model runs out to six months) and twenty years of history.
 * The output is arithmetic on those series, compared with how much water each
 * crop needs over its season. Every figure can be traced back to its inputs.
 */
public final class PlantingAdvisor {

    /** Approximate seasonal water need in mm (FAO irrigation and drainage papers 33 and 56). */
    public record Crop(String code, String name, int days, double needMinMm, double needMaxMm) {
    }

    public static final List<Crop> CROPS = List.of(
            new Crop("MAIZE", "Maize", 150, 500, 800),
            new Crop("BEANS", "Beans", 90, 300, 500),
            new Crop("POTATO", "Potato", 110, 500, 700),
            new Crop("TOMATO", "Tomato", 120, 400, 600),
            new Crop("SORGHUM", "Sorghum", 110, 350, 600));

    /** One 30-day block: what the seasonal model expects against what usually falls. */
    public record Block(LocalDate from, LocalDate to, Double forecastMm, Double typicalMm) {
    }

    /** One possible planting date for a crop and how the season starting then looks. */
    public record Option(LocalDate start, Double expectedMm, Double dryYearMm, String verdict,
                         boolean modelBacked) {
    }

    public record CropAdvice(String code, String name, int seasonDays, LocalDate harvestBy,
                             double needMinMm, double needMaxMm,
                             Double expectedMm, Double forecastMedianMm, Double typicalMedianMm,
                             Double dryYearMm, Double wetYearMm,
                             String verdict, String reason, List<Option> options, Option best) {
    }

    public record Timing(String status, LocalDate plantFrom, String message) {
    }

    public record Rain(double past7Mm, double next7Mm, double next14Mm) {
    }

    public record Result(LocalDate today, Rain rain, List<Double> next16Days, Timing timing,
                         List<Block> blocks, List<CropAdvice> crops, String headline,
                         List<String> caveats) {
    }

    private PlantingAdvisor() {
    }

    /**
     * @param today     the first day of the forecast and seasonal series
     * @param past7     rainfall for the 7 days before today (may be shorter if unknown)
     * @param next16    forecast rainfall from today for up to 16 days
     * @param members   seasonal ensemble members, each a daily series starting today
     * @param history   daily observed rainfall for many past years, keyed by date
     */
    public static Result advise(LocalDate today, double[] past7, double[] next16,
                                List<double[]> members, Map<LocalDate, Double> history) {
        Rain rain = new Rain(sum(past7, 0, past7.length), sum(next16, 0, 7), sum(next16, 0, 14));
        Timing timing = timing(today, next16);

        List<Block> blocks = new ArrayList<>();
        for (int b = 0; b < 6; b++) {
            LocalDate from = today.plusDays(30L * b);
            LocalDate to = from.plusDays(29);
            double[] perMember = memberTotals(members, 30 * b, 30);
            double forecast = perMember.length == 0 ? Double.NaN : percentile(perMember, 50);
            double typical = percentile(historyTotals(history, from, 30), 50);
            blocks.add(new Block(from, to, round(forecast), round(typical)));
        }

        List<CropAdvice> crops = new ArrayList<>();
        for (Crop crop : CROPS) {
            crops.add(assess(crop, today, members, history));
        }
        crops.sort(Comparator.comparingInt((CropAdvice c) -> rank(c.verdict()))
                .thenComparingDouble(c -> c.expectedMm() == null ? 0 : Math.abs(c.expectedMm() - mid(c))));

        List<String> caveats = new ArrayList<>();
        caveats.add("Seasonal forecasts have limited skill beyond a few weeks; the history column is "
                + "the safer guide to what is usual here.");
        caveats.add("This looks at rain only. Soil type, altitude frost, pests and seed variety "
                + "also decide what to plant: confirm with your agronomist.");
        if (members.isEmpty()) {
            caveats.add("The seasonal model did not answer, so advice rests on history alone.");
        }
        return new Result(today, rain, round(next16), timing, blocks, crops,
                headline(timing, crops), caveats);
    }

    /**
     * The usual agronomic onset rule: planting rain is 20 mm over three days, followed by
     * enough rain that the seedlings are not stranded. Only the 16-day forecast can be checked.
     */
    static Timing timing(LocalDate today, double[] next16) {
        int n = next16.length;
        for (int i = 0; i + 2 < n; i++) {
            if (sum(next16, i, i + 3) < 20) {
                continue;
            }
            int end = Math.min(n, i + 13);
            if (end - (i + 3) < 7) {
                continue; // too little forecast left to check the weeks after
            }
            if (sum(next16, i + 3, end) < 12) {
                continue; // a burst of rain followed by a dry spell: seedlings would die
            }
            LocalDate day = today.plusDays(i);
            if (i <= 3) {
                return new Timing("PLANT_NOW", day, "Planting rain arrives " + (i == 0 ? "today"
                        : "on " + day) + ": at least 20 mm over three days with rain to follow.");
            }
            return new Timing("PLANT_SOON", day, "Planting rain is forecast from " + day
                    + ". Prepare the seedbed now and plant when it falls.");
        }
        return new Timing("WAIT", null, "No planting rain in the next " + n
                + " days. Planting now needs irrigation; otherwise wait for the next forecast.");
    }

    /**
     * Every fortnight for nine months. Where the seasonal model reaches the whole season the
     * answer blends model and history; beyond that it rests on history alone (modelBacked=false).
     */
    static List<Option> options(Crop crop, LocalDate today, List<double[]> members,
                                Map<LocalDate, Double> history) {
        List<Option> out = new ArrayList<>();
        for (int offset = 0; offset <= 270; offset += 14) {
            CropAdvice a = assess(crop, today, offset, members, history, false);
            out.add(new Option(today.plusDays(offset), a.expectedMm(), a.dryYearMm(), a.verdict(),
                    a.forecastMedianMm() != null));
        }
        return out;
    }

    /**
     * The earliest date with the best verdict: a farmer wants to plant as soon as it is right,
     * not in the wettest month. When nothing works, the wettest start is the least bad.
     */
    static Option best(List<Option> options) {
        Option top = options.stream().min(Comparator
                .comparingInt((Option o) -> rank(o.verdict()))
                .thenComparing(Option::start)).orElse(null);
        if (top != null && rank(top.verdict()) == 2) {
            return options.stream().max(Comparator.comparingDouble(
                    (Option o) -> o.expectedMm() == null ? 0 : o.expectedMm())).orElse(top);
        }
        return top;
    }

    static CropAdvice assess(Crop crop, LocalDate today, List<double[]> members,
                             Map<LocalDate, Double> history) {
        return assess(crop, today, 0, members, history, true);
    }

    static CropAdvice assess(Crop crop, LocalDate today, int offset, List<double[]> members,
                             Map<LocalDate, Double> history, boolean withOptions) {
        LocalDate start = today.plusDays(offset);
        double[] fc = memberTotals(members, offset, crop.days());
        double[] hist = historyTotals(history, start, crop.days());
        double fcMedian = fc.length == 0 ? Double.NaN : percentile(fc, 50);
        double histMedian = percentile(hist, 50);
        double dry = percentile(hist, 10);
        double wet = percentile(hist, 90);
        // Both views, equally weighted: the forecast knows this year, history knows the spread.
        double expected = Double.isNaN(fcMedian) ? histMedian : (fcMedian + histMedian) / 2;

        String verdict;
        String reason;
        if (expected >= crop.needMinMm() && dry >= 0.75 * crop.needMinMm()) {
            verdict = "SUITABLE";
            reason = String.format("About %.0f mm expected over %d days against %.0f-%.0f mm needed, "
                    + "and even a dry year (%.0f mm) is close to enough.",
                    expected, crop.days(), crop.needMinMm(), crop.needMaxMm(), dry);
        } else if (expected >= crop.needMinMm()) {
            verdict = "MARGINAL";
            reason = String.format("About %.0f mm expected but a dry year gives only %.0f mm against "
                    + "%.0f mm needed. Plant only with irrigation to fall back on.",
                    expected, dry, crop.needMinMm());
        } else {
            verdict = "NOT_SUITABLE";
            reason = String.format("About %.0f mm expected over %d days, below the %.0f mm this crop "
                    + "needs. It would need irrigation for the shortfall.",
                    expected, crop.days(), crop.needMinMm());
        }
        boolean diseasePronePotato = crop.code().equals("POTATO") || crop.code().equals("TOMATO");
        if (expected > crop.needMaxMm() * 1.6 && diseasePronePotato) {
            if (verdict.equals("SUITABLE")) {
                verdict = "MARGINAL";
            }
            reason += String.format(" Very wet (%.0f mm): expect blight and poor drainage.", expected);
        }
        List<Option> opts = withOptions ? options(crop, today, members, history) : List.of();
        return new CropAdvice(crop.code(), crop.name(), crop.days(), start.plusDays(crop.days()),
                crop.needMinMm(), crop.needMaxMm(), round(expected), round(fcMedian), round(histMedian),
                round(dry), round(wet), verdict, reason, opts, best(opts));
    }

    static String headline(Timing timing, List<CropAdvice> crops) {
        List<String> good = names(crops, "SUITABLE");
        List<String> fair = names(crops, "MARGINAL");
        String now;
        if (!good.isEmpty()) {
            now = "Suited to the rain expected if planted now: " + String.join(", ", good) + ".";
        } else if (!fair.isEmpty()) {
            now = "Nothing is clearly suited right now. Possible only with irrigation as backup: "
                    + String.join(", ", fair) + ".";
        } else {
            now = "No crop is suited to the rain expected if planted now; it would need irrigation.";
        }
        StringBuilder later = new StringBuilder();
        for (CropAdvice c : crops) {
            if (!c.verdict().equals("SUITABLE") && c.best() != null
                    && c.best().verdict().equals("SUITABLE") && later.length() < 140) {
                later.append(later.length() == 0 ? " Better dates: " : "; ")
                        .append(c.name()).append(" from ").append(c.best().start());
            }
        }
        if (later.length() > 0) {
            later.append('.');
        }
        String lead = switch (timing.status()) {
            case "PLANT_NOW" -> "Planting rain has arrived. ";
            case "PLANT_SOON" -> "Planting rain is coming from " + timing.plantFrom() + ". ";
            default -> "No planting rain is forecast yet. ";
        };
        return lead + now + later;
    }

    private static List<String> names(List<CropAdvice> crops, String verdict) {
        return crops.stream().filter(c -> c.verdict().equals(verdict)).map(CropAdvice::name).toList();
    }

    // ---- statistics -------------------------------------------------------------------

    /** Total rain over `days` days starting `offset` days in, one value per ensemble member. */
    static double[] memberTotals(List<double[]> members, int offset, int days) {
        return members.stream()
                .filter(m -> m.length >= offset + days)
                .mapToDouble(m -> sum(m, offset, offset + days)).toArray();
    }

    /** The same calendar window in each year of history, one total per year with full data. */
    static double[] historyTotals(Map<LocalDate, Double> history, LocalDate start, int days) {
        List<Double> totals = new ArrayList<>();
        for (int year = start.getYear() - 25; year < start.getYear(); year++) {
            LocalDate from;
            try {
                from = LocalDate.of(year, start.getMonth(), start.getDayOfMonth());
            } catch (java.time.DateTimeException leap) {
                from = LocalDate.of(year, start.getMonth(), 28);
            }
            double total = 0;
            int seen = 0;
            for (int d = 0; d < days; d++) {
                Double mm = history.get(from.plusDays(d));
                if (mm != null) {
                    total += mm;
                    seen++;
                }
            }
            if (seen == days) {
                totals.add(total);
            }
        }
        return totals.stream().mapToDouble(Double::doubleValue).toArray();
    }

    static double percentile(double[] values, double p) {
        if (values.length == 0) {
            return Double.NaN;
        }
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        double rank = p / 100.0 * (sorted.length - 1);
        int lo = (int) Math.floor(rank);
        int hi = (int) Math.ceil(rank);
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (rank - lo);
    }

    private static double sum(double[] a, int from, int to) {
        double s = 0;
        for (int i = from; i < Math.min(to, a.length); i++) {
            s += a[i];
        }
        return s;
    }

    private static int rank(String verdict) {
        return switch (verdict) {
            case "SUITABLE" -> 0;
            case "MARGINAL" -> 1;
            default -> 2;
        };
    }

    private static double mid(CropAdvice c) {
        return (c.needMinMm() + c.needMaxMm()) / 2;
    }

    private static Double round(double v) {
        return Double.isNaN(v) ? null : Math.round(v * 10) / 10.0;
    }

    private static List<Double> round(double[] values) {
        List<Double> out = new ArrayList<>();
        for (double v : values) {
            out.add(round(v));
        }
        return out;
    }
}
