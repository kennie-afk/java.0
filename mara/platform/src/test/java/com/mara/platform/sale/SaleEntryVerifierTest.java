package com.mara.platform.sale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mara.platform.journal.ChainDigest;
import com.mara.platform.journal.JournalEntry;
import com.mara.platform.journal.JournalVerifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The server's verifier against a journal the terminal's own TypeScript produced and
 * signed (apps/terminal/test/ts-journal-fixture.test.ts). If the canonical encoding, the
 * digest chain or the signature handling drifted on either side, these fail.
 */
class SaleEntryVerifierTest {

    private static final ObjectMapper JSON = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static JsonNode fixture;

    @BeforeAll
    static void load() throws Exception {
        Path path = Path.of("../apps/terminal/test/fixtures/ts-journal.json");
        fixture = JSON.readTree(Files.readString(path));
    }

    private static SaleEntryVerifier.Verified verify(JsonNode e, String publicKey) throws Exception {
        SaleBody sale = JSON.treeToValue(e.get("sale"), SaleBody.class);
        return SaleEntryVerifier.verify(
                publicKey, e.get("terminalId").asText(), e.get("sequence").asLong(),
                e.get("epochSecond").asLong(), e.get("nano").asInt(), sale,
                e.get("bodyDigest").asText(), e.get("previousDigest").asText(),
                e.get("digest").asText(), e.get("signature").asText());
    }

    @Test
    void everyEntryTheTerminalSignedVerifiesAndTheChainLinks() throws Exception {
        String key = fixture.get("publicKeySpkiBase64").asText();
        List<JournalEntry> entries = new ArrayList<>();
        for (JsonNode e : fixture.get("entries")) {
            SaleEntryVerifier.Verified v = verify(e, key);
            assertEquals(SaleEntryVerifier.Verdict.OK, v.verdict(), "sequence " + e.get("sequence"));
            entries.add(v.entry());
        }
        assertEquals(4, entries.size());

        var verdict = new JournalVerifier().verify(
                JournalVerifier.Cursor.fresh(fixture.get("terminalId").asText()), entries);
        assertEquals(4, verdict.accepted().size());
        assertTrue(verdict.gaps().isEmpty());
        assertTrue(verdict.breaks().isEmpty());
    }

    @Test
    void theTerminalsSalesAreArithmeticallyConsistent() throws Exception {
        for (JsonNode e : fixture.get("entries")) {
            SaleBody sale = JSON.treeToValue(e.get("sale"), SaleBody.class);
            SaleCheck.Result r = SaleCheck.check(sale);
            assertTrue(r.findings().isEmpty(), "sequence " + e.get("sequence") + ": " + r.findings());
            assertEquals(r.totalMinor(), r.netMinor() + r.taxMinor());
        }
    }

    @Test
    void editingAnyFieldOfASaleIsDetectedFromTheBodyDigest() throws Exception {
        String key = fixture.get("publicKeySpkiBase64").asText();
        JsonNode e = fixture.get("entries").get(1).deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) e.get("sale").get("lines").get(0)).put("unitMinor", "1");
        assertEquals(SaleEntryVerifier.Verdict.BODY_DIGEST_MISMATCH, verify(e, key).verdict());
    }

    @Test
    void aSignatureFromAnotherKeyIsRefused() throws Exception {
        // The fixture's own key is the only one that signed it; any other key must fail.
        String other = "MCowBQYDK2VwAyEAGb9ECWmEzf6FQbrBZ9w7lshQhqowtrbLDFOzuJbEmyo=";
        JsonNode e = fixture.get("entries").get(0);
        assertEquals(SaleEntryVerifier.Verdict.BAD_SIGNATURE, verify(e, other).verdict());
    }

    @Test
    void aForgedChainDigestIsRefusedBeforeItsSignatureIsConsidered() throws Exception {
        String key = fixture.get("publicKeySpkiBase64").asText();
        com.fasterxml.jackson.databind.node.ObjectNode e = fixture.get("entries").get(2).deepCopy();
        e.put("sequence", 9);
        assertEquals(SaleEntryVerifier.Verdict.DIGEST_MISMATCH, verify(e, key).verdict());
    }

    @Test
    void deletingASaleFromTheUploadLeavesAGapNamingIt() throws Exception {
        String key = fixture.get("publicKeySpkiBase64").asText();
        List<JournalEntry> entries = new ArrayList<>();
        for (JsonNode e : fixture.get("entries")) {
            if (e.get("sequence").asLong() == 3) {
                continue;
            }
            entries.add(verify(e, key).entry());
        }
        var verdict = new JournalVerifier().verify(
                JournalVerifier.Cursor.fresh(fixture.get("terminalId").asText()), entries);
        assertEquals(2, verdict.accepted().size());
        assertEquals(1, verdict.gaps().size());
        assertEquals(3L, verdict.gaps().get(0).fromSequence());
    }

    @Test
    void jsonStringMatchesJavaScriptsEscapingForTheAwkwardCases() {
        assertEquals("\"a\\\"b\\\\c\\n\\t\\u0001\"", SaleCanonical.jsonString("a\"b\\c\n\t\u0001"));
        assertEquals("\"\u00e9 \u2615 日本\"", SaleCanonical.jsonString("\u00e9 \u2615 日本"));
        assertEquals("\"\\ud800\"", SaleCanonical.jsonString("\ud800"));
        assertEquals("\"\ud83d\ude00\"", SaleCanonical.jsonString("\ud83d\ude00"));
    }

    @Test
    void aRequestTheTerminalSignedVerifiesUnderTheServersRequestSignature() {
        JsonNode r = fixture.get("request");
        String key = fixture.get("publicKeySpkiBase64").asText();
        byte[] body = r.get("body").asText().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String terminal = fixture.get("terminalId").asText();
        long at = r.get("epochSecond").asLong();
        assertTrue(com.mara.platform.identity.RequestSignature.verify(
                key, terminal, at, r.get("method").asText(), r.get("path").asText(), body, r.get("signature").asText()));
        assertTrue(!com.mara.platform.identity.RequestSignature.verify(
                key, terminal, at, "POST", "/v1/terminal/fiscal/leases", body, r.get("signature").asText()));
    }

    @Test
    void chainDigestHelperAgreesWithItself() {
        assertTrue(ChainDigest.matches(ChainDigest.GENESIS, new byte[32]));
    }
}
