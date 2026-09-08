package com.smartseason.agronomy.intelligence;

import com.smartseason.agronomy.intelligence.AdvisoryDtos.Candidate;
import com.smartseason.agronomy.intelligence.AdvisoryDtos.DiagnosisRequest;
import com.smartseason.agronomy.intelligence.AdvisoryDtos.DiagnosisResponse;
import com.smartseason.agronomy.intelligence.AdvisoryDtos.SeasonForecastRequest;
import com.smartseason.agronomy.intelligence.AdvisoryDtos.SeasonForecastResponse;
import com.smartseason.agronomy.intelligence.AdvisoryDtos.StageOutlook;
import com.smartseason.agronomy.intelligence.AdvisoryDtos.WeatherOutlookRequest;
import com.smartseason.agronomy.intelligence.AdvisoryDtos.WeatherOutlookResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic agronomy, used when no model is configured.
 *
 * The numbers here are ordinary Kenyan smallholder figures - days to maturity
 * and typical rainfed yields per hectare - not invented precision. Every answer
 * says it came from rules, and the confidence stays modest to match.
 */
public class HeuristicAdvisoryModel implements AdvisoryModel {

    /** Days from planting to harvest, and typical rainfed yield in kg per hectare. */
    private record CropProfile(int daysToMaturity, double yieldKgPerHa, List<String> stages) {
    }

    private static final Map<String, CropProfile> CROPS = Map.of(
            "MAIZE", new CropProfile(150, 2700,
                    List.of("Emergence", "Vegetative", "Tasseling", "Grain fill", "Maturity")),
            "POTATO", new CropProfile(110, 20000,
                    List.of("Emergence", "Vegetative", "Tuber initiation", "Tuber bulking", "Maturity")),
            "BEANS", new CropProfile(90, 900,
                    List.of("Emergence", "Vegetative", "Flowering", "Pod fill", "Maturity")),
            "AVOCADO", new CropProfile(240, 9000,
                    List.of("Flowering", "Fruit set", "Fruit growth", "Maturity")),
            "TOMATO", new CropProfile(120, 25000,
                    List.of("Emergence", "Vegetative", "Flowering", "Fruit set", "Maturity")));

    private static final CropProfile DEFAULT_PROFILE =
            new CropProfile(120, 3000, List.of("Emergence", "Vegetative", "Reproductive", "Maturity"));

    /** Symptom words that point at a specific problem, per crop. */
    private record Signature(String code, String commonName, String scientific,
                             String crops, List<String> keywords, String management) {
    }

    private static final List<Signature> SIGNATURES = List.of(
            new Signature("FAW", "Fall armyworm", "Spodoptera frugiperda", "MAIZE,SORGHUM",
                    List.of("window", "whorl", "frass", "ragged", "holes", "caterpillar", "worm"),
                    "Scout twice weekly. Spray only when 20% of plants show fresh damage, "
                            + "early morning or late evening, directing the nozzle into the whorl."),
            new Signature("MLN", "Maize lethal necrosis", "MCMV + SCMV", "MAIZE",
                    List.of("mottling", "yellow streak", "stunted", "dead heart", "necrosis"),
                    "Rogue and burn affected plants. Do not save seed. Control thrips and "
                            + "aphids, and rotate out of maize for one season."),
            new Signature("MSV", "Maize streak", "Maize streak virus", "MAIZE",
                    List.of("streak", "chlorotic", "stripe", "pale lines"),
                    "Control leafhoppers early. Plant tolerant varieties and avoid staggered "
                            + "planting next to older maize."),
            new Signature("LATE_BLIGHT", "Late blight", "Phytophthora infestans", "POTATO,TOMATO",
                    List.of("water-soaked", "lesion", "brown patch", "white mould", "blight", "rot"),
                    "Apply a protectant fungicide before rain and a systemic once lesions "
                            + "appear. Remove volunteers and avoid overhead irrigation."),
            new Signature("EARLY_BLIGHT", "Early blight", "Alternaria solani", "POTATO,TOMATO",
                    List.of("target spot", "concentric", "ring", "lower leaves", "yellowing"),
                    "Remove lower affected leaves, mulch to stop soil splash, and keep the "
                            + "crop's nitrogen up."),
            new Signature("BACTERIAL_WILT", "Bacterial wilt", "Ralstonia solanacearum", "POTATO,TOMATO",
                    List.of("wilt", "collapse", "ooze", "brown vascular", "sudden"),
                    "There is no chemical cure. Remove and destroy affected plants with the "
                            + "soil around them, and rotate away from solanaceous crops for two seasons."),
            new Signature("BEAN_RUST", "Bean rust", "Uromyces appendiculatus", "BEANS",
                    List.of("rust", "pustule", "orange", "powder", "brown spot"),
                    "Spray at first pustules. Widen spacing so the canopy dries faster."),
            new Signature("APHIDS", "Aphids", "Aphidoidea", "MAIZE,BEANS,POTATO,TOMATO",
                    List.of("curl", "sticky", "honeydew", "sooty", "cluster", "aphid"),
                    "Encourage ladybirds before spraying. If numbers keep rising, use a "
                            + "selective aphicide rather than a broad-spectrum product."),
            new Signature("NUTRIENT_N", "Nitrogen deficiency", null, "MAIZE,BEANS,POTATO,TOMATO",
                    List.of("pale", "yellow older leaves", "v-shape", "stunted growth", "light green"),
                    "Top-dress nitrogen and confirm with a soil test. Yellowing that starts "
                            + "on the oldest leaves usually means nitrogen, not disease."));

    @Override
    public String source() {
        return "RULES";
    }

    @Override
    public DiagnosisResponse diagnose(DiagnosisRequest request) {
        String crop = upper(request.cropCode());
        String text = request.symptoms() == null ? "" : request.symptoms().toLowerCase(Locale.ROOT);

        List<Candidate> candidates = new ArrayList<>();
        for (Signature signature : SIGNATURES) {
            if (!signature.crops().contains(crop)) {
                continue;
            }
            long hits = signature.keywords().stream().filter(text::contains).count();
            if (hits == 0) {
                continue;
            }
            // Confidence rises with the number of matching words but never
            // pretends to be certain: this is word matching, not diagnosis.
            double confidence = Math.min(0.75, 0.35 + 0.12 * hits);
            candidates.add(new Candidate(
                    signature.code(), signature.commonName(), signature.scientific(), confidence,
                    "Matched " + hits + " of " + signature.keywords().size()
                            + " described signs for this problem."));
        }

        candidates.sort(Comparator.comparingDouble(Candidate::confidence).reversed());
        List<Candidate> ranked =
                candidates.size() > 3 ? List.copyOf(candidates.subList(0, 3)) : List.copyOf(candidates);

        List<String> caveats = new ArrayList<>();
        caveats.add("Produced by keyword rules, not a trained model. Treat it as a starting point.");
        if (request.images() != null && !request.images().isEmpty()) {
            caveats.add("Photographs were not examined: no vision model is configured.");
        }
        if (ranked.isEmpty()) {
            caveats.add("Nothing matched the description. Have an agronomist look at the crop.");
        }

        String management = ranked.isEmpty()
                ? "No confident match. Record a scouting report with photographs and ask an agronomist."
                : SIGNATURES.stream()
                        .filter(s -> s.code().equals(ranked.getFirst().code()))
                        .findFirst().map(Signature::management).orElse("");

        String severity = ranked.isEmpty() ? "INFO"
                : ranked.getFirst().confidence() >= 0.6 ? "HIGH" : "MEDIUM";

        return new DiagnosisResponse(ranked, severity, management,
                ranked.isEmpty() ? "Collect more evidence" : "Scout the block and confirm before spraying",
                source(), caveats);
    }

    @Override
    public SeasonForecastResponse forecastSeason(SeasonForecastRequest request) {
        CropProfile profile = CROPS.getOrDefault(upper(request.cropCode()), DEFAULT_PROFILE);
        LocalDate start = LocalDate.parse(request.startDate());
        LocalDate harvest = start.plusDays(profile.daysToMaturity());

        double area = request.areaHa() == null ? 1.0 : request.areaHa();
        double projected = profile.yieldKgPerHa() * area;

        List<String> risks = new ArrayList<>();
        double factor = 1.0;

        if (request.recentRainfallMm() != null) {
            if (request.recentRainfallMm() < 20) {
                factor -= 0.20;
                risks.add("Rainfall over the last week is low for this stage; expect moisture stress.");
            } else if (request.recentRainfallMm() > 200) {
                factor -= 0.10;
                risks.add("Very wet: watch for foliar disease and check drainage.");
            }
        }
        if (request.averageTempC() != null) {
            if (request.averageTempC() > 30) {
                factor -= 0.12;
                risks.add("Average temperature above 30C reduces grain and tuber fill.");
            } else if (request.averageTempC() < 12) {
                factor -= 0.08;
                risks.add("Cool conditions will slow development and push harvest later.");
            }
        }
        if (risks.isEmpty()) {
            risks.add("No weather stress evident from the figures supplied.");
        }

        projected = Math.round(projected * Math.max(0.4, factor));

        // Even segments across the crop's stage list: enough to plan against,
        // and honest about being a calendar rather than a model.
        List<StageOutlook> stages = new ArrayList<>();
        int span = Math.max(1, profile.daysToMaturity() / profile.stages().size());
        LocalDate cursor = start;
        for (String stage : profile.stages()) {
            LocalDate end = cursor.plusDays(span);
            stages.add(new StageOutlook(stage, cursor, end, guidanceFor(stage)));
            cursor = end;
        }

        return new SeasonForecastResponse(
                harvest, projected, 0.5, stages, risks,
                String.format(Locale.ROOT,
                        "%s planted %s should reach harvest around %s, yielding roughly %.0f kg "
                                + "from %.2f ha on typical rainfed performance.",
                        capitalise(request.cropCode()), start, harvest, projected, area),
                source());
    }

    @Override
    public WeatherOutlookResponse weatherOutlook(WeatherOutlookRequest request) {
        List<String> advisories = new ArrayList<>();
        String irrigation = "No irrigation needed on the figures supplied.";
        String spray = "Any calm, dry morning this week.";

        Double rain = request.rainfallLast7DaysMm();
        Double moisture = request.soilMoisturePct();

        if (moisture != null && moisture < 25) {
            irrigation = "Soil moisture is below 25%. Irrigate within 48 hours.";
            advisories.add("Irrigate before the next heat of the day to limit loss.");
        } else if (rain != null && rain < 15) {
            irrigation = "Little rain in the last week. Plan to irrigate if none falls in three days.";
        }

        if (rain != null && rain > 100) {
            advisories.add("Heavy rain raises blight and rust pressure. Scout before it dries.");
            spray = "Hold off spraying until 24 hours without rain, or it will wash off.";
        }
        if (request.averageTempC() != null && request.averageTempC() > 28) {
            advisories.add("Spray in the early morning: above 28C, drift and evaporation waste product.");
        }
        if (advisories.isEmpty()) {
            advisories.add("Conditions are unremarkable. Keep to the normal scouting round.");
        }

        return new WeatherOutlookResponse(
                rain != null && rain > 100 ? "Wet" : rain != null && rain < 15 ? "Dry" : "Mixed",
                advisories, irrigation, spray, source());
    }

    private static String guidanceFor(String stage) {
        return switch (stage) {
            case "Emergence" -> "Check the stand and gap-fill within a week of emergence.";
            case "Vegetative" -> "Top-dress nitrogen and keep the crop weed-free.";
            case "Tasseling", "Flowering" -> "The crop is most sensitive to moisture stress now.";
            case "Tuber initiation" -> "Earth up and keep moisture steady to set tubers.";
            case "Grain fill", "Pod fill", "Tuber bulking", "Fruit growth" ->
                    "Protect the canopy: yield is being laid down now.";
            case "Maturity" -> "Plan labour, drying and storage before the crop is ready.";
            default -> "Scout weekly and record what you find.";
        };
    }

    private static String upper(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String capitalise(String value) {
        String lower = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return lower.isEmpty() ? lower : Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
