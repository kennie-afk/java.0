package com.mara.identity.admin;

import java.util.regex.Pattern;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Argon2id for staff PINs. A four-digit PIN has 10,000 possibilities, so the only thing
 * between a stolen table and every cashier's PIN is what each guess costs.
 */
@Component
public class PinHasher {

    private static final Pattern SHAPE = Pattern.compile("^\\d{4,8}$");

    private final Argon2PasswordEncoder encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();

    /** Verified against when the staff number does not exist, so both paths cost the same. */
    private final String decoy = encoder.encode("00000000");

    public String hash(String pin) {
        return encoder.encode(pin);
    }

    public boolean matches(String pin, String storedHash) {
        return encoder.matches(pin, storedHash == null ? decoy : storedHash);
    }

    /** Four to eight digits, and not one of the PINs an attacker tries first. */
    public static String refusalFor(String pin) {
        if (pin == null || !SHAPE.matcher(pin).matches()) {
            return "A PIN is 4 to 8 digits.";
        }
        if (pin.chars().distinct().count() == 1) {
            return "That PIN is too easy to guess (all the same digit).";
        }
        boolean ascending = true;
        boolean descending = true;
        for (int i = 1; i < pin.length(); i++) {
            ascending &= pin.charAt(i) == pin.charAt(i - 1) + 1;
            descending &= pin.charAt(i) == pin.charAt(i - 1) - 1;
        }
        if (ascending || descending) {
            return "That PIN is too easy to guess (a run of digits).";
        }
        return null;
    }
}
