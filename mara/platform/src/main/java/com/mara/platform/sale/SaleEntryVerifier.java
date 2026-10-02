package com.mara.platform.sale;

import com.mara.platform.identity.TerminalSignature;
import com.mara.platform.journal.ChainDigest;
import com.mara.platform.journal.JournalEntry;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Checks one uploaded journal entry on its own, before it is ever chained: the body digest
 * the terminal claims must be the digest of the sale it sent, the chain digest must follow
 * from it, and the signature over that chain digest must verify against the terminal's
 * enrolled key. Chain continuity (ordering, gaps, forks) is a separate question answered by
 * {@link com.mara.platform.journal.JournalVerifier}.
 *
 * <p>Nothing here trusts a digest the terminal supplied: both are recomputed.
 */
public final class SaleEntryVerifier {

    public enum Verdict {
        OK,
        MALFORMED,
        BODY_DIGEST_MISMATCH,
        DIGEST_MISMATCH,
        BAD_SIGNATURE
    }

    public record Verified(Verdict verdict, JournalEntry entry, byte[] digest) {
    }

    private SaleEntryVerifier() {
    }

    public static Verified verify(
            String publicKeyBase64,
            String terminalId,
            long sequence,
            long epochSecond,
            int nano,
            SaleBody sale,
            String bodyDigestHex,
            String previousDigestHex,
            String digestHex,
            String signatureHex) {
        JournalEntry entry;
        byte[] claimedDigest;
        byte[] signature;
        try {
            if (nano < 0 || nano > 999_999_999) {
                return new Verified(Verdict.MALFORMED, null, null);
            }
            byte[] recomputedBody = SaleCanonical.bodyDigest(sale);
            if (!ChainDigest.matches(recomputedBody, HexFormat.of().parseHex(bodyDigestHex))) {
                return new Verified(Verdict.BODY_DIGEST_MISMATCH, null, null);
            }
            entry = new JournalEntry(
                    terminalId, sequence, Instant.ofEpochSecond(epochSecond, nano),
                    recomputedBody, HexFormat.of().parseHex(previousDigestHex));
            claimedDigest = HexFormat.of().parseHex(digestHex);
            signature = HexFormat.of().parseHex(signatureHex);
        } catch (RuntimeException e) {
            return new Verified(Verdict.MALFORMED, null, null);
        }
        byte[] digest = ChainDigest.of(entry);
        if (!ChainDigest.matches(digest, claimedDigest)) {
            return new Verified(Verdict.DIGEST_MISMATCH, entry, digest);
        }
        if (!TerminalSignature.verify(publicKeyBase64, digest, signature)) {
            return new Verified(Verdict.BAD_SIGNATURE, entry, digest);
        }
        return new Verified(Verdict.OK, entry, digest);
    }
}
