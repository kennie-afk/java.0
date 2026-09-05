package com.smartseason.payment.mpesa;

public final class Msisdn {

    private Msisdn() {
    }

    public static String normalise(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Phone number is required");
        }

        String digits = raw.replaceAll("[^0-9+]", "");
        if (digits.startsWith("+")) {
            digits = digits.substring(1);
        }

        if (digits.startsWith("07") || digits.startsWith("01")) {
            digits = "254" + digits.substring(1);
        } else if (digits.startsWith("7") || digits.startsWith("1")) {
            digits = "254" + digits;
        }

        if (!digits.matches("254[17]\\d{8}")) {
            throw new IllegalArgumentException("Not a Kenyan mobile number: " + raw);
        }
        return digits;
    }
}
