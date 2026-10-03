package com.mara.platform.credential;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The shape of an operator or service credential: {@code mop_<keyId>.<secret>}.
 *
 * <p>The key id is public and is how the server finds the row; the secret is 32 random bytes
 * (256 bits) and is shown exactly once, at issue. The server stores only SHA-256 of the secret.
 * A fast hash is right here and a password hash would be wrong: the secret already has 256 bits
 * of entropy, so there is nothing for a slow hash to protect, and verification happens on every
 * back-office call. Comparison is constant-time.
 */
public final class OperatorToken {

    public static final String PREFIX = "mop_";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern FORMAT = Pattern.compile("^mop_([0-9a-f]{16})\\.([A-Za-z0-9_-]{43})$");

    /** A parsed token: what the server looks up by, and the secret it checks the hash of. */
    public record Parsed(String keyId, String secret) {
    }

    /** A freshly minted token and the two parts the server keeps. */
    public record Minted(String token, String keyId, byte[] secretHash) {
    }

    private OperatorToken() {
    }

    public static Minted mint() {
        byte[] id = new byte[8];
        byte[] secret = new byte[32];
        RANDOM.nextBytes(id);
        RANDOM.nextBytes(secret);
        String keyId = HexFormat.of().formatHex(id);
        String secretText = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        return new Minted(PREFIX + keyId + "." + secretText, keyId, hash(secretText));
    }

    /** Empty for anything that is not exactly the token format: nothing partial is ever accepted. */
    public static Optional<Parsed> parse(String presented) {
        if (presented == null) {
            return Optional.empty();
        }
        var m = FORMAT.matcher(presented.trim());
        return m.matches() ? Optional.of(new Parsed(m.group(1), m.group(2))) : Optional.empty();
    }

    public static byte[] hash(String secret) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** Constant-time check that {@code secret} is the one whose hash is stored. */
    public static boolean matches(String secret, byte[] storedHash) {
        return storedHash != null && MessageDigest.isEqual(hash(secret), storedHash);
    }
}
