package com.mara.sync;

import com.mara.platform.identity.RequestSignature;
import com.mara.platform.journal.ChainDigest;
import com.mara.platform.journal.JournalEntry;
import com.mara.platform.sale.SaleBody;
import com.mara.platform.sale.SaleCanonical;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A terminal in Java: it keeps a real hash chain and signs it and its requests, as the till does. */
final class TestTerminal {

    final String id = "TERM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 20).toUpperCase();
    final String tenantId;
    final KeyPair key;
    private byte[] head = ChainDigest.GENESIS;
    private long sequence = 0;
    private long clock = 1_760_000_000L;

    TestTerminal(String tenantId) {
        this.tenantId = tenantId;
        try {
            this.key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    String publicKey() {
        return Base64.getEncoder().encodeToString(key.getPublic().getEncoded());
    }

    static SaleBody sale(long unit, int qty, int taxBp, String fiscalNumber) {
        long net = unit * qty;
        long tax = com.mara.platform.money.Money.of(net, "KES").percentage(taxBp).minor();
        long total = net + tax;
        return new SaleBody(SaleBody.V1, "KES",
                List.of(new SaleBody.Line("SKU-" + unit, "Item " + unit, qty, String.valueOf(unit), taxBp,
                        String.valueOf(net), String.valueOf(tax))),
                List.of(new SaleBody.Payment("CASH", String.valueOf(total), "", String.valueOf(total))),
                String.valueOf(total),
                fiscalNumber == null ? new SaleBody.Fiscal(SaleBody.Fiscal.PENDING, "")
                        : new SaleBody.Fiscal(SaleBody.Fiscal.NUMBERED, fiscalNumber),
                null);
    }

    /** Appends a sale to this terminal's own journal and returns the upload record for it. */
    Map<String, Object> record(SaleBody sale) {
        sequence++;
        clock += 5;
        Map<String, Object> e = build(sequence, clock, sale, head);
        head = HexFormat.of().parseHex((String) e.get("digest"));
        return e;
    }

    /** An entry for an arbitrary sequence and previous digest, signed with this terminal's key. */
    Map<String, Object> build(long seq, long epochSecond, SaleBody sale, byte[] previous) {
        byte[] body = SaleCanonical.bodyDigest(sale);
        byte[] digest = ChainDigest.of(new JournalEntry(id, seq, Instant.ofEpochSecond(epochSecond), body, previous));
        HexFormat hex = HexFormat.of();
        return new java.util.LinkedHashMap<>(Map.of(
                "sequence", seq, "terminalId", id, "epochSecond", epochSecond, "nano", 0,
                "sale", sale, "bodyDigest", hex.formatHex(body), "previousDigest", hex.formatHex(previous),
                "digest", hex.formatHex(digest), "signature", sign(digest)));
    }

    byte[] headDigest() {
        return head;
    }

    String sign(byte[] message) {
        try {
            Signature s = Signature.getInstance("Ed25519");
            s.initSign(key.getPrivate());
            s.update(message);
            return HexFormat.of().formatHex(s.sign());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Headers for a signed request to {@code target} (path plus query). */
    Map<String, String> signedHeaders(String method, String target, byte[] body, long at) {
        String sig = sign(RequestSignature.message(id, at, method, target, body));
        return Map.of("X-Mara-Terminal", id, "X-Mara-Timestamp", String.valueOf(at), "X-Mara-Signature", sig);
    }

    static List<Map<String, Object>> list(Map<String, Object>... entries) {
        return new ArrayList<>(List.of(entries));
    }
}
