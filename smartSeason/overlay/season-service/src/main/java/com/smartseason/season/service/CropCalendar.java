package com.smartseason.season.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The standard stage calendar for the crops we advise on, used when an organisation has not
 * defined its own stage templates. Lengths are ordinary Kenyan smallholder figures and add up
 * to the days-to-maturity the planting advisor uses, so the two never disagree.
 */
public final class CropCalendar {

    public record Step(String name, int days) {
        /** Stored on the season as a code, e.g. "Tuber bulking" becomes TUBER_BULKING. */
        public String code() {
            return name.toUpperCase(Locale.ROOT).replace(' ', '_');
        }
    }

    private static final Map<String, List<Step>> DEFAULTS = Map.of(
            "MAIZE", List.of(new Step("Emergence", 10), new Step("Vegetative", 50),
                    new Step("Tasseling", 21), new Step("Grain fill", 49), new Step("Maturity", 20)),
            "POTATO", List.of(new Step("Emergence", 14), new Step("Vegetative", 26),
                    new Step("Tuber initiation", 20), new Step("Tuber bulking", 35), new Step("Maturity", 15)),
            "BEANS", List.of(new Step("Emergence", 7), new Step("Vegetative", 28),
                    new Step("Flowering", 15), new Step("Pod fill", 25), new Step("Maturity", 15)),
            "TOMATO", List.of(new Step("Emergence", 10), new Step("Vegetative", 30),
                    new Step("Flowering", 20), new Step("Fruit set", 30), new Step("Maturity", 30)),
            "SORGHUM", List.of(new Step("Emergence", 8), new Step("Vegetative", 35),
                    new Step("Flowering", 17), new Step("Grain fill", 35), new Step("Maturity", 15)));

    private CropCalendar() {
    }

    /** Empty for a crop we have no calendar for: the season is then created without stages. */
    public static List<Step> forCrop(String cropCode) {
        return DEFAULTS.getOrDefault(cropCode == null ? "" : cropCode.trim().toUpperCase(Locale.ROOT), List.of());
    }
}
