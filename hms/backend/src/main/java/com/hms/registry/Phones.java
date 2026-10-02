package com.hms.registry;

/** Kenyan mobile numbers in one canonical form (+2547XXXXXXXX), so the same person matches. */
public final class Phones {
    private Phones() {}

    /** Returns the canonical number, or null for blank. Throws for something that cannot be a Kenyan number. */
    public static String normalise(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String digits = raw.replaceAll("[\\s\\-().]", "");
        if (digits.startsWith("+")) {
            digits = digits.substring(1);
        }
        if (digits.startsWith("0") && digits.length() == 10) {
            digits = "254" + digits.substring(1);
        }
        if (digits.matches("254[17]\\d{8}")) {
            return "+" + digits;
        }
        throw new IllegalArgumentException("not a Kenyan mobile number");
    }
}
