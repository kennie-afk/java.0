package com.mara.platform.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

/**
 * A single-use code that lets an owner admit one terminal to their tenant.
 *
 * <p>Enrolment is the moment a machine stops being a stranger, so it is the moment worth
 * attacking. The design assumes the code will be read aloud across a shop floor,
 * written on a sticky note and photographed — because it will be — and limits the damage
 * accordingly:
 *
 * <ul>
 *   <li><b>Single use.</b> Redemption is a state transition, not a comparison. A code
 *       that has enrolled a terminal cannot enrol a second one.</li>
 *   <li><b>Short lived.</b> Fifteen minutes by default. Long enough to walk to the till,
 *       too short to be useful from a photograph found later.</li>
 *   <li><b>Stored as a hash.</b> The plaintext exists only in the owner's hands. A dump
 *       of the enrolment table does not let an attacker enrol anything.</li>
 *   <li><b>Compared in constant time.</b> Redemption must not be a timing oracle that
 *       lets an attacker walk the code one character at a time.</li>
 * </ul>
 *
 * <p>The alphabet omits the characters people mistake for one another — no 0/O, no 1/I/L
 * — because a code that has to be re-read is a code that gets written down somewhere
 * permanent.
 */
public final class EnrolmentCode {

    /** Crockford-style alphabet: unambiguous when spoken or hand-written. */
    private static final char[] ALPHABET = "23456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    private static final int LENGTH = 10;
    private static final int GROUP = 5;

    public static final Duration DEFAULT_VALIDITY = Duration.ofMinutes(15);

    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Domain separator. Keeps these digests from colliding with any other SHA-256 the
     * system computes over user-supplied text.
     */
    private static final byte[] DOMAIN = "mara.enrolment.v1".getBytes(StandardCharsets.UTF_8);

    private EnrolmentCode() {
    }

    /**
     * Generates a fresh code.
     *
     * <p>Ten characters from a 30-symbol alphabet is about 49 bits of entropy — far
     * beyond guessing at any rate an enrolment endpoint would tolerate, while staying
     * short enough to read over a counter.
     */
    public static String generate() {
        StringBuilder code = new StringBuilder(LENGTH + 1);
        for (int i = 0; i < LENGTH; i++) {
            if (i == GROUP) {
                code.append('-');
            }
            code.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return code.toString();
    }

    /**
     * Normalises user input before hashing or comparison.
     *
     * <p>Someone typing a code will lower-case it, drop the hyphen, or add spaces. All
     * three must land on the same value, or valid codes fail for cosmetic reasons and
     * the owner reissues — which is worse for security than accepting the variation.
     */
    public static String normalise(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder cleaned = new StringBuilder(input.length());
        for (char c : input.toUpperCase().toCharArray()) {
            if (Character.isLetterOrDigit(c)) {
                cleaned.append(c);
            }
        }
        return cleaned.toString();
    }

    /**
     * Hashes a code for storage and lookup.
     *
     * <p>Not salted per tenant, and that is deliberate rather than an omission. A device
     * presenting a code has no tenant yet — resolving the code is <em>how</em> it learns
     * which tenant it belongs to — so a tenant-salted hash could never be looked up.
     * Salting would make the code unfindable by the only party who needs to find it.
     *
     * <p>What replaces the salt is the code's own scarcity. Codes are 49 bits of
     * {@link SecureRandom} output, globally unique, single use, and dead after fifteen
     * minutes. Reversing a SHA-256 over that space takes hours on serious hardware
     * against a value that stops being worth anything in minutes, and a code resolves to
     * exactly one tenant, so there is no second tenant to replay it against.
     */
    public static byte[] hash(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(DOMAIN);
            digest.update((byte) 0x1f);   // separator, so the prefix cannot be shifted
            digest.update(normalise(code).getBytes(StandardCharsets.UTF_8));
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Constant-time comparison, so redemption cannot be used as a timing oracle. */
    public static boolean matches(byte[] storedHash, String submitted) {
        return MessageDigest.isEqual(storedHash, hash(submitted));
    }

    public static boolean isExpired(Instant issuedAt, Duration validity, Instant now) {
        return !now.isBefore(issuedAt.plus(validity));
    }
}
