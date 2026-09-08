package com.mara.platform.identity;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Objects;

/**
 * Verifies that a journal entry was signed by the terminal it claims to come from.
 *
 * <p>Transport security is not enough on its own. mTLS proves who opened the connection,
 * but a sale outlives its TLS session by years — it has to be attributable while sitting
 * at rest in a table, to an auditor who was never party to the handshake. So each entry
 * carries an Ed25519 signature over its chain digest, and the signature is stored beside
 * it.
 *
 * <p>Ed25519 rather than RSA or ECDSA because it needs no per-signature randomness. ECDSA
 * with a repeated nonce leaks the private key outright, and a cheap till is exactly the
 * environment where entropy is weakest — a machine that boots from the same image every
 * morning with no hardware RNG. Ed25519's determinism removes that failure mode instead
 * of hoping the terminal gets it right.
 *
 * <p>Verification only. This class cannot sign, because the server has no business
 * holding a terminal's private key — it never leaves the device that generated it.
 */
public final class TerminalSignature {

    private static final String ALGORITHM = "Ed25519";

    private TerminalSignature() {
    }

    /**
     * Checks {@code signature} against {@code signedBytes} using the terminal's
     * registered public key.
     *
     * <p>Returns false for a malformed key or signature rather than throwing. A
     * terminal sending rubbish is an authentication failure, not an internal error,
     * and must take the same path as an honest terminal with the wrong key — otherwise
     * the difference between "bad key" and "bad signature" leaks through the response.
     */
    public static boolean verify(PublicKey publicKey, byte[] signedBytes, byte[] signature) {
        Objects.requireNonNull(publicKey, "publicKey");
        Objects.requireNonNull(signedBytes, "signedBytes");
        Objects.requireNonNull(signature, "signature");
        try {
            Signature verifier = Signature.getInstance(ALGORITHM);
            verifier.initVerify(publicKey);
            verifier.update(signedBytes);
            return verifier.verify(signature);
        } catch (GeneralSecurityException e) {
            return false;
        }
    }

    /** Convenience overload for keys held in their stored base64 form. */
    public static boolean verify(String publicKeyBase64, byte[] signedBytes, byte[] signature) {
        PublicKey key = decodePublicKey(publicKeyBase64);
        return key != null && verify(key, signedBytes, signature);
    }

    /**
     * Decodes an X.509 SubjectPublicKeyInfo in base64, as submitted at enrolment.
     *
     * @return the key, or null if it is not a well-formed Ed25519 public key
     */
    public static PublicKey decodePublicKey(String base64) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        try {
            byte[] der = Base64.getDecoder().decode(base64);
            return KeyFactory.getInstance(ALGORITHM).generatePublic(new X509EncodedKeySpec(der));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return null;
        }
    }

    /** True when a submitted key is usable, without revealing why it is not. */
    public static boolean isWellFormed(String publicKeyBase64) {
        return decodePublicKey(publicKeyBase64) != null;
    }
}
