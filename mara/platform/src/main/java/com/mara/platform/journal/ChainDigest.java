package com.mara.platform.journal;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Computes the per-terminal hash chain described in ARCHITECTURE.md §2.3.
 *
 * <p>Each entry commits to the one before it, so altering a historic sale invalidates
 * every digest after it. Forging a single line item means rewriting and re-signing the
 * entire tail of the journal — while the server already holds the originals.
 *
 * <p>Field lengths are written before variable-length fields. Without that, a terminal
 * could move bytes across a boundary — shifting a character from the end of one field to
 * the start of the next — and produce two different entries with the same digest. Length
 * prefixing makes the encoding unambiguous.
 */
public final class ChainDigest {

    /** What entry 1 chains onto: 32 zero bytes, meaning "nothing precedes this". */
    public static final byte[] GENESIS = new byte[32];

    private static final String ALGORITHM = "SHA-256";

    private ChainDigest() {
    }

    public static byte[] of(JournalEntry entry) {
        MessageDigest digest = newDigest();
        digest.update(entry.previousDigest());
        digest.update(lengthPrefixed(entry.terminalId()));
        digest.update(longBytes(entry.sequence()));
        digest.update(longBytes(entry.occurredAt().getEpochSecond()));
        digest.update(longBytes(entry.occurredAt().getNano()));
        digest.update(lengthPrefixed(entry.bodyDigest()));
        return digest.digest();
    }

    /**
     * Digests a sale body from its canonical field encoding.
     *
     * <p>Callers pass fields in a fixed order; each is length-prefixed for the same
     * reason as above.
     */
    public static byte[] body(byte[]... fields) {
        MessageDigest digest = newDigest();
        for (byte[] field : fields) {
            digest.update(lengthPrefixed(field));
        }
        return digest.digest();
    }

    public static byte[] utf8(String value) {
        return value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] longBytes(long value) {
        return ByteBuffer.allocate(Long.BYTES).putLong(value).array();
    }

    public static String hex(byte[] digest) {
        return HexFormat.of().formatHex(digest);
    }

    public static byte[] fromHex(String hex) {
        return HexFormat.of().parseHex(hex);
    }

    /** Constant-time comparison, so digest checks cannot be probed by timing. */
    public static boolean matches(byte[] a, byte[] b) {
        return MessageDigest.isEqual(a, b);
    }

    private static byte[] lengthPrefixed(String value) {
        return lengthPrefixed(utf8(value));
    }

    private static byte[] lengthPrefixed(byte[] value) {
        return ByteBuffer.allocate(Integer.BYTES + value.length)
                .putInt(value.length)
                .put(value)
                .array();
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JLS for every conforming JRE.
            throw new IllegalStateException(ALGORITHM + " unavailable", e);
        }
    }
}
