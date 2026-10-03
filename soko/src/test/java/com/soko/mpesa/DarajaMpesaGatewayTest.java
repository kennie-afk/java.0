package com.soko.mpesa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class DarajaMpesaGatewayTest {

    private static MpesaProperties props(String secret) {
        return new MpesaProperties("live", null, "key", "secret", "174379", "pass", secret, "https://soko.example/v1/public/mpesa/stk-callback");
    }

    @Test
    void liveModeRefusesToStartWithoutACallbackSecret() {
        assertThrows(IllegalStateException.class, () -> new DarajaMpesaGateway(props(null), new ObjectMapper()));
        assertThrows(IllegalStateException.class, () -> new DarajaMpesaGateway(props(""), new ObjectMapper()));
        assertThrows(IllegalStateException.class, () -> new DarajaMpesaGateway(props("too-short"), new ObjectMapper()));
    }

    @Test
    void onlyTheExactSecretIsAccepted() {
        var g = new DarajaMpesaGateway(props("a-callback-secret-of-16+"), new ObjectMapper());
        assertTrue(g.verifyCallbackSignature("{}", "a-callback-secret-of-16+"));
        assertFalse(g.verifyCallbackSignature("{}", "a-callback-secret-of-16-"));
        assertFalse(g.verifyCallbackSignature("{}", ""));
        assertFalse(g.verifyCallbackSignature("{}", null));
    }

    @Test
    void theRegisteredCallbackUrlCarriesTheEncodedSecret() {
        var g = new DarajaMpesaGateway(props("s3cret value+16chars"), new ObjectMapper());
        assertEquals("https://x/cb?secret=s3cret+value%2B16chars", g.withSecret("https://x/cb"));
        assertEquals("https://x/cb?a=1&secret=s3cret+value%2B16chars", g.withSecret("https://x/cb?a=1"));
    }
}
