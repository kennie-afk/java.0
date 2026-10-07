package com.soko.payment;

import com.soko.platform.Errors;

/** Kenyan mobile numbers in the one form Daraja accepts: 2547XXXXXXXX or 2541XXXXXXXX. */
public final class Msisdn {

    private Msisdn() {}

    public static String normalise(String raw) {
        String digits = raw == null ? "" : raw.replaceAll("[\\s\\-()]", "");
        if (digits.startsWith("+")) {
            digits = digits.substring(1);
        }
        if (digits.startsWith("0")) {
            digits = "254" + digits.substring(1);
        }
        if (!digits.matches("254[17]\\d{8}")) {
            throw new Errors.BadRequest("enter a Safaricom number such as 0712 345 678");
        }
        return digits;
    }
}
