package com.mara.platform.identity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Proof that a request to a server came from a particular enrolled terminal.
 *
 * <p>The terminal signs, with the Ed25519 key it enrolled with, a message that binds
 * the terminal, the moment, the verb, the path and a digest of the body. Replaying a
 * captured request elsewhere (another path, another body) fails the signature; replaying
 * it to the same path later fails the freshness window. This is application-layer proof
 * of identity for an endpoint that has no gateway in front of it yet; with mTLS in front,
 * it still answers the question TLS cannot: which terminal key authored <em>this</em>
 * request body.
 */
public final class RequestSignature {

    public static final Duration MAX_SKEW = Duration.ofMinutes(5);

    private RequestSignature() {
    }

    public static byte[] message(String terminalId, long epochSecond, String method, String path, byte[] body) {
        return ("mara.request.v1|" + terminalId + "|" + epochSecond + "|" + method.toUpperCase() + "|" + path
                + "|" + sha256Hex(body)).getBytes(StandardCharsets.UTF_8);
    }

    public static boolean isFresh(long epochSecond, Instant now) {
        return Math.abs(Duration.between(Instant.ofEpochSecond(epochSecond), now).toSeconds())
                <= MAX_SKEW.toSeconds();
    }

    public static boolean verify(
            String publicKeyBase64, String terminalId, long epochSecond, String method, String path,
            byte[] body, String signatureHex) {
        try {
            byte[] signature = HexFormat.of().parseHex(signatureHex);
            return TerminalSignature.verify(
                    publicKeyBase64, message(terminalId, epochSecond, method, path, body), signature);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static String sha256Hex(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
