package com.smartseason.notification.dispatch;

import java.util.List;
import java.util.Locale;

public final class LocaleResolver {

    public static final String DEFAULT_LOCALE = "en";
    private static final List<String> SUPPORTED = List.of("en", "sw");

    private LocaleResolver() {
    }

    public static String resolve(String requested) {
        if (requested == null || requested.isBlank()) {
            return DEFAULT_LOCALE;
        }
        String base = requested.toLowerCase(Locale.ROOT).split("[-_]")[0];
        return SUPPORTED.contains(base) ? base : DEFAULT_LOCALE;
    }

    public static List<String> fallbackChain(String requested) {
        String resolved = resolve(requested);
        return resolved.equals(DEFAULT_LOCALE)
                ? List.of(DEFAULT_LOCALE)
                : List.of(resolved, DEFAULT_LOCALE);
    }
}
