package com.mara.platform.identity;

import static org.junit.jupiter.api.Assertions.*;

import com.mara.platform.journal.ChainDigest;
import com.mara.platform.journal.JournalEntry;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Signature verification is the last line between the ledger and a machine that is
 * lying about who it is, so these tests are about forgery rather than happy paths.
 */
class TerminalSignatureTest {

    private KeyPair terminal;
    private KeyPair impostor;

    @BeforeEach
    void generateKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
        terminal = generator.generateKeyPair();
        impostor = generator.generateKeyPair();
    }

    @Test
    void acceptsASaleSignedByTheEnrolledTerminal() throws Exception {
        byte[] digest = ChainDigest.of(sale(1L));
        byte[] signature = sign(terminal, digest);

        assertTrue(TerminalSignature.verify(terminal.getPublic(), digest, signature));
        assertTrue(TerminalSignature.verify(base64(terminal), digest, signature));
    }

    @Test
    @DisplayName("a sale signed by another terminal's key is refused")
    void refusesAnotherTerminalsSignature() throws Exception {
        byte[] digest = ChainDigest.of(sale(1L));
        byte[] forged = sign(impostor, digest);

        assertFalse(TerminalSignature.verify(terminal.getPublic(), digest, forged));
    }

    @Test
    @DisplayName("altering the sale after signing invalidates the signature")
    void refusesATamperedPayload() throws Exception {
        byte[] original = ChainDigest.of(sale(1L));
        byte[] signature = sign(terminal, original);

        // The same terminal, the same key — but a different sale.
        byte[] altered = ChainDigest.of(sale(2L));

        assertFalse(TerminalSignature.verify(terminal.getPublic(), altered, signature));
    }

    @Test
    void refusesAFlippedBitInTheSignature() throws Exception {
        byte[] digest = ChainDigest.of(sale(1L));
        byte[] signature = sign(terminal, digest);
        signature[signature.length / 2] ^= 0x01;

        assertFalse(TerminalSignature.verify(terminal.getPublic(), digest, signature));
    }

    @Test
    @DisplayName("malformed input fails closed instead of throwing")
    void malformedInputIsAnAuthenticationFailure() {
        // A terminal sending rubbish must take the same path as one with the wrong key,
        // or the difference leaks through the response.
        assertFalse(TerminalSignature.verify("not base64 at all", new byte[] {1}, new byte[] {2}));
        assertFalse(TerminalSignature.verify(Base64.getEncoder().encodeToString(new byte[] {1, 2, 3}),
                new byte[] {1}, new byte[] {2}));
        assertFalse(TerminalSignature.verify(base64(terminal), new byte[] {1}, new byte[0]));
    }

    @Test
    void recognisesWellFormedKeysAndRejectsEverythingElse() {
        assertTrue(TerminalSignature.isWellFormed(base64(terminal)));
        assertFalse(TerminalSignature.isWellFormed(null));
        assertFalse(TerminalSignature.isWellFormed(""));
        assertFalse(TerminalSignature.isWellFormed("   "));
        assertFalse(TerminalSignature.isWellFormed("////not-a-key////"));
    }

    @Test
    void aRevokedKeyIsStillMathematicallyValidWhichIsWhyStatusIsCheckedSeparately() throws Exception {
        // Revocation is a policy decision, not a cryptographic one. The signature on a
        // revoked terminal's old sales must still verify, because those sales are real
        // money that belongs in the ledger — see TerminalStatus.mayUpload().
        byte[] digest = ChainDigest.of(sale(1L));
        byte[] signature = sign(terminal, digest);

        assertTrue(TerminalSignature.verify(terminal.getPublic(), digest, signature));
        assertFalse(TerminalStatus.REVOKED.maySell());
        assertTrue(TerminalStatus.REVOKED.mayUpload());
    }

    private static JournalEntry sale(long sequence) {
        return new JournalEntry(
                "TERM-LANE-04",
                sequence,
                Instant.parse("2026-09-06T08:00:00Z").plusSeconds(sequence * 60),
                ChainDigest.body(ChainDigest.utf8("sale"), ChainDigest.longBytes(sequence * 1_250L)),
                ChainDigest.GENESIS);
    }

    private static byte[] sign(KeyPair pair, byte[] payload) throws Exception {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(pair.getPrivate());
        signer.update(payload);
        return signer.sign();
    }

    private static String base64(KeyPair pair) {
        return Base64.getEncoder().encodeToString(pair.getPublic().getEncoded());
    }
}
