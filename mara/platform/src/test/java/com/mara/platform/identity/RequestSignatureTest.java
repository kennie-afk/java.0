package com.mara.platform.identity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class RequestSignatureTest {

    private static final byte[] BODY = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);

    private static String sign(KeyPair kp, String terminal, long at, String method, String path, byte[] body)
            throws Exception {
        Signature s = Signature.getInstance("Ed25519");
        s.initSign(kp.getPrivate());
        s.update(RequestSignature.message(terminal, at, method, path, body));
        return HexFormat.of().formatHex(s.sign());
    }

    @Test
    void aSignedRequestVerifiesAndAnyChangeToItDoesNot() throws Exception {
        KeyPair kp = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String pub = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
        String sig = sign(kp, "TERM-1", 1000, "POST", "/v1/sync/journal", BODY);

        assertTrue(RequestSignature.verify(pub, "TERM-1", 1000, "POST", "/v1/sync/journal", BODY, sig));
        assertFalse(RequestSignature.verify(pub, "TERM-2", 1000, "POST", "/v1/sync/journal", BODY, sig));
        assertFalse(RequestSignature.verify(pub, "TERM-1", 1001, "POST", "/v1/sync/journal", BODY, sig));
        assertFalse(RequestSignature.verify(pub, "TERM-1", 1000, "GET", "/v1/sync/journal", BODY, sig));
        assertFalse(RequestSignature.verify(pub, "TERM-1", 1000, "POST", "/v1/fiscal/leases", BODY, sig));
        assertFalse(RequestSignature.verify(pub, "TERM-1", 1000, "POST", "/v1/sync/journal",
                "{\"a\":2}".getBytes(StandardCharsets.UTF_8), sig));
        assertFalse(RequestSignature.verify(pub, "TERM-1", 1000, "POST", "/v1/sync/journal", BODY, "zz"));
    }

    @Test
    void freshnessIsAFiveMinuteWindowEitherWay() {
        Instant now = Instant.ofEpochSecond(10_000);
        assertTrue(RequestSignature.isFresh(10_000 - 299, now));
        assertTrue(RequestSignature.isFresh(10_000 + 299, now));
        assertFalse(RequestSignature.isFresh(10_000 - 301, now));
        assertFalse(RequestSignature.isFresh(10_000 + 301, now));
    }
}
