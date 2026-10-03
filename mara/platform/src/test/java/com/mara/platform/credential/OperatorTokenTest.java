package com.mara.platform.credential;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class OperatorTokenTest {

    @Test
    void aMintedTokenParsesAndItsSecretMatchesTheStoredHashOnly() {
        var minted = OperatorToken.mint();
        var parsed = OperatorToken.parse(minted.token()).orElseThrow();
        assertEquals(minted.keyId(), parsed.keyId());
        assertEquals(32, minted.secretHash().length);
        assertTrue(OperatorToken.matches(parsed.secret(), minted.secretHash()));
        assertFalse(OperatorToken.matches(parsed.secret() + "x", minted.secretHash()));
        assertFalse(OperatorToken.matches(OperatorToken.mint().token(), minted.secretHash()));
        assertFalse(minted.token().contains(HexFormat.of().formatHex(minted.secretHash())));
    }

    @Test
    void tokensAreUniqueAndHaveRealEntropy() {
        var seen = new HashSet<String>();
        for (int i = 0; i < 2000; i++) {
            assertTrue(seen.add(OperatorToken.mint().token()));
        }
        // 43 url-safe base64 characters = 256 bits
        assertEquals(4 + 16 + 1 + 43, OperatorToken.mint().token().length());
    }

    @Test
    void anythingThatIsNotExactlyTheFormatIsRefused() {
        var good = OperatorToken.mint().token();
        for (String bad : new String[] {null, "", " ", "Bearer " + good, good + "x", good.substring(1), good.replace("mop_", "mop-"),
                good.replace(".", ":"), "mop_zzzzzzzzzzzzzzzz." + "A".repeat(43), "mop_" + "0".repeat(16) + "." + "A".repeat(42),
                "test-admin-token-0123456789-abcdef"}) {
            assertTrue(OperatorToken.parse(bad).isEmpty(), "should refuse: " + bad);
        }
        assertTrue(OperatorToken.parse("  " + good + "  ").isPresent());   // surrounding blanks are trimmed
    }

    @Test
    void scopesCannotExceedWhatTheKindAndBindingAllow() {
        assertNull(Scopes.problem("OPERATOR", null, List.of(Scopes.ADMIN_READ, Scopes.PLATFORM_TENANTS)));
        assertNull(Scopes.problem("OPERATOR", "TEN-1", List.of(Scopes.ADMIN_READ, Scopes.ADMIN_WRITE)));
        assertTrue(Scopes.problem("OPERATOR", "TEN-1", List.of(Scopes.PLATFORM_TENANTS)).contains("platform"));
        assertTrue(Scopes.problem("OPERATOR", "TEN-1", List.of(Scopes.CREDENTIALS_MANAGE)).contains("platform"));
        assertTrue(Scopes.problem("OPERATOR", null, List.of(Scopes.TERMINALS_LOOKUP)).contains("service credentials only"));
        assertNull(Scopes.problem("SERVICE", null, List.of(Scopes.TERMINALS_LOOKUP, Scopes.SYNC_FEED)));
        assertTrue(Scopes.problem("SERVICE", null, List.of(Scopes.ADMIN_WRITE)).contains("not services"));
        assertTrue(Scopes.problem("OPERATOR", null, List.of("admin:everything")).contains("unknown scope"));
        assertNotNull(Scopes.problem("OPERATOR", null, List.of()));
    }
}
