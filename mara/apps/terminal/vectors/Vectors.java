import com.mara.platform.fiscal.FiscalLease;
import com.mara.platform.identity.TerminalSignature;
import com.mara.platform.journal.ChainDigest;
import com.mara.platform.journal.ChainVerdict;
import com.mara.platform.journal.JournalEntry;
import com.mara.platform.journal.JournalVerifier;
import com.mara.platform.journal.JournalVerifier.Cursor;
import com.mara.platform.money.Money;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.function.Supplier;

/**
 * Emits test vectors by running the platform's own Java classes, so the TypeScript port
 * in src/lib is asserted against what the Java code actually computes rather than against
 * a re-reading of the spec.
 *
 * Run with the platform classes on the classpath (see vectors/generate.sh):
 *   java -cp platform/target/classes vectors/Vectors.java out.json [ts-signature.json]
 *
 * If a second argument is given, it is a signature produced by the browser's WebCrypto;
 * this program verifies it with the platform's real TerminalSignature and prints the result.
 */
public class Vectors {

    static String q(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20 || c > 0x7e) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.append('"').toString();
    }

    static String arr(List<String> items) {
        return "[" + String.join(",", items) + "]";
    }

    static String obj(String... kv) {
        StringBuilder b = new StringBuilder("{");
        for (int i = 0; i < kv.length; i += 2) {
            if (i > 0) b.append(',');
            b.append(q(kv[i])).append(':').append(kv[i + 1]);
        }
        return b.append('}').toString();
    }

    static String hex(byte[] b) { return q(ChainDigest.hex(b)); }
    static String num(long v) { return q(Long.toString(v)); }   // int64 as string: JSON numbers lose precision

    // ------------------------------------------------------------------ money

    static String moneyVectors() {
        long[] amounts = {0, 1, 2, 3, 99, 100, 101, 1234, -1, -2, -3, -99, -100, -101, 999_999_999_999L,
            -999_999_999_999L, Long.MAX_VALUE, Long.MIN_VALUE + 1};
        int[] parts = {1, 2, 3, 4, 5, 7, 10, 100};
        List<String> alloc = new ArrayList<>();
        for (long a : amounts) {
            for (int p : parts) {
                Money[] out = Money.of(a, "KES").allocate(p);
                List<String> r = new ArrayList<>();
                for (Money m : out) r.add(num(m.minor()));
                alloc.add(obj("minor", num(a), "parts", String.valueOf(p), "result", arr(r)));
            }
        }
        List<String> allocErr = new ArrayList<>();
        for (int p : new int[] {0, -1}) {
            try {
                Money.of(10, "KES").allocate(p);
                allocErr.add(obj("parts", String.valueOf(p), "throws", "false"));
            } catch (IllegalArgumentException e) {
                allocErr.add(obj("parts", String.valueOf(p), "throws", "true"));
            }
        }

        long[] pcAmounts = {0, 1, 5, 9, 10, 50, 99, 100, 333, 1250, 12345, -1, -5, -50, -333, -12345, 1_000_000_007L,
            -1_000_000_007L};
        long[] bps = {0, 1, 50, 100, 500, 800, 1000, 1600, 1650, 2500, 10000, 12345};
        List<String> pct = new ArrayList<>();
        for (long a : pcAmounts) {
            for (long bp : bps) {
                pct.add(obj("minor", num(a), "bp", num(bp), "result", num(Money.of(a, "KES").percentage(bp).minor())));
            }
        }
        List<String> pctErr = new ArrayList<>();
        pctErr.add(obj("case", q("negativeBp"), "minor", num(100), "bp", num(-1), "kind",
                thrown(() -> Money.of(100, "KES").percentage(-1))));
        pctErr.add(obj("case", q("overflow"), "minor", num(Long.MAX_VALUE), "bp", num(2), "kind",
                thrown(() -> Money.of(Long.MAX_VALUE, "KES").percentage(2))));

        List<String> arith = new ArrayList<>();
        arith.add(obj("op", q("plus"), "a", num(Long.MAX_VALUE), "b", num(1), "kind",
                thrown(() -> Money.of(Long.MAX_VALUE, "KES").plus(Money.of(1, "KES")))));
        arith.add(obj("op", q("minus"), "a", num(Long.MIN_VALUE), "b", num(1), "kind",
                thrown(() -> Money.of(Long.MIN_VALUE, "KES").minus(Money.of(1, "KES")))));
        arith.add(obj("op", q("times"), "a", num(Long.MAX_VALUE), "b", num(2), "kind",
                thrown(() -> Money.of(Long.MAX_VALUE, "KES").times(2))));
        arith.add(obj("op", q("negate"), "a", num(Long.MIN_VALUE), "b", num(0), "kind",
                thrown(() -> Money.of(Long.MIN_VALUE, "KES").negated())));
        arith.add(obj("op", q("plus"), "a", num(5), "b", num(7), "kind", q("ok"), "result", num(12)));

        List<String> fmt = new ArrayList<>();
        String[] currencies = {"KES", "USD", "EUR", "GBP", "TZS", "UGX", "RWF", "JPY", "TND"};
        long[] fmtAmounts = {0, 5, -5, 99, 100, 123456, -123456, -7, 1_000_000_00, 12, 1234567};
        for (String c : currencies) {
            int digits = java.util.Currency.getInstance(c).getDefaultFractionDigits();
            for (long a : fmtAmounts) {
                fmt.add(obj("currency", q(c), "digits", String.valueOf(digits), "minor", num(a),
                        "text", q(Money.of(a, c).toString())));
            }
        }
        return obj("allocate", arr(alloc), "allocateErrors", arr(allocErr), "percentage", arr(pct),
                "percentageErrors", arr(pctErr), "arithmetic", arr(arith), "format", arr(fmt));
    }

    static String thrown(Runnable r) {
        try {
            r.run();
            return q("none");
        } catch (ArithmeticException e) {
            return q("arithmetic");
        } catch (IllegalArgumentException e) {
            return q("illegalArgument");
        }
    }

    // ---------------------------------------------------------------- digests

    static byte[] fill(int n, int seed) {
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) b[i] = (byte) (seed * 31 + i * 7);
        return b;
    }

    static String digestVectors() {
        String[] terminals = {"TERM-LANE-04", "TERM-AB12CD34", "T", "TÉRM-日本-🏪", "with|pipe and space"};
        long[][] times = {{0, 0}, {1_788_000_000L, 0}, {1_788_000_000L, 123_000_000}, {1_788_000_000L, 999_000_000},
            {253_402_300_799L, 999_999_999}};
        long[] seqs = {1, 2, 255, 256, 65_536, 4_294_967_297L, Long.MAX_VALUE};
        List<String> entries = new ArrayList<>();
        int seed = 0;
        for (String t : terminals) {
            for (int i = 0; i < seqs.length; i++) {
                long[] tm = times[(i + seed) % times.length];
                byte[] prev = seqs[i] == 1 ? ChainDigest.GENESIS : fill(32, seed + i);
                byte[] body = fill(32, seed * 3 + i);
                JournalEntry e = new JournalEntry(t, seqs[i], Instant.ofEpochSecond(tm[0], tm[1]), body, prev);
                entries.add(obj("terminalId", q(t), "sequence", num(seqs[i]), "epochSecond", num(tm[0]),
                        "nano", num(tm[1]), "bodyDigest", hex(body), "previousDigest", hex(prev),
                        "digest", hex(ChainDigest.of(e))));
            }
            seed++;
        }

        List<String> bodies = new ArrayList<>();
        // Same field list the terminal's sale encoding uses (src/lib/sale-body.ts).
        String[][] sales = {
            {"mara.sale.v1", "KES", "[]", "[]", "0", "FISCAL_PENDING:"},
            {"mara.sale.v1", "KES", "[[\"SKU-1\",\"Unga 2kg\",\"2\",\"14500\",\"0\",\"29000\",\"0\"]]",
                "[[\"CASH\",\"29000\",\"\"]]", "29000", "FISCAL_PENDING:"},
            {"mara.sale.v1", "KES", "[[\"K\\u00e9\",\"Ma\\u017e \\ud83c\\udf4e\",\"1\",\"100\",\"1600\",\"100\",\"16\"]]",
                "[[\"MOBILE_MONEY\",\"116\",\"QFT9\"]]", "116", "NUMBERED:100042"},
        };
        for (String[] s : sales) {
            byte[] d = ChainDigest.body(ChainDigest.utf8(s[0]), ChainDigest.utf8(s[1]), ChainDigest.utf8(s[2]),
                    ChainDigest.utf8(s[3]), ChainDigest.longBytes(Long.parseLong(s[4])), ChainDigest.utf8(s[5]));
            bodies.add(obj("version", q(s[0]), "currency", q(s[1]), "lines", q(s[2]), "payments", q(s[3]),
                    "total", num(Long.parseLong(s[4])), "fiscal", q(s[5]), "bodyDigest", hex(d)));
        }

        List<String> raw = new ArrayList<>();
        byte[][][] fieldSets = {{}, {new byte[0]}, {new byte[] {1, 2, 3}, new byte[0], new byte[] {4}},
            {new byte[] {1, 2}, new byte[] {3}}, {new byte[] {1}, new byte[] {2, 3}}};
        for (byte[][] fs : fieldSets) {
            List<String> f = new ArrayList<>();
            for (byte[] x : fs) f.add(hex(x));
            raw.add(obj("fields", arr(f), "digest", hex(ChainDigest.body(fs))));
        }
        // Chain of 25 built by Java, so a whole chain (not just single links) is compared.
        List<String> chain = new ArrayList<>();
        byte[] prev = ChainDigest.GENESIS;
        Instant t = Instant.parse("2026-09-29T08:00:00Z");
        for (int i = 1; i <= 25; i++) {
            byte[] body = ChainDigest.body(ChainDigest.utf8("sale-" + i), ChainDigest.longBytes(i * 1000L));
            JournalEntry e = new JournalEntry("TERM-CHAIN", i, t.plusMillis(i * 1500L), body, prev);
            byte[] d = ChainDigest.of(e);
            chain.add(obj("sequence", num(i), "epochSecond", num(e.occurredAt().getEpochSecond()),
                    "nano", num(e.occurredAt().getNano()), "bodyDigest", hex(body), "previousDigest", hex(prev),
                    "digest", hex(d)));
            prev = d;
        }
        return obj("genesis", hex(ChainDigest.GENESIS), "entries", arr(entries), "saleBodies", arr(bodies),
                "rawBodies", arr(raw), "chain", arr(chain));
    }

    // --------------------------------------------------------------- verifier

    static String entryJson(JournalEntry e) {
        return obj("terminalId", q(e.terminalId()), "sequence", num(e.sequence()),
                "epochSecond", num(e.occurredAt().getEpochSecond()), "nano", num(e.occurredAt().getNano()),
                "bodyDigest", hex(e.bodyDigest()), "previousDigest", hex(e.previousDigest()));
    }

    static String cursorJson(Cursor c) {
        return obj("terminalId", q(c.terminalId()), "lastSequence", num(c.lastSequence()),
                "headDigest", hex(c.headDigest()), "genesisDigest", hex(c.genesisDigest()),
                "lastEpochSecond", num(c.lastOccurredAt().getEpochSecond()),
                "lastNano", num(c.lastOccurredAt().getNano()));
    }

    static final String TERM = "TERM-LANE-04";
    static final Instant T0 = Instant.parse("2026-09-06T08:00:00Z");
    static final JournalVerifier V = new JournalVerifier();

    static List<JournalEntry> chain(String term, byte[] head, long from, int n, Instant start) {
        List<JournalEntry> out = new ArrayList<>();
        byte[] prev = head;
        for (int i = 0; i < n; i++) {
            long seq = from + i;
            JournalEntry e = new JournalEntry(term, seq, start.plusSeconds(seq * 10), ChainDigest.body(
                    ChainDigest.utf8("body-" + seq)), prev);
            out.add(e);
            prev = ChainDigest.of(e);
        }
        return out;
    }

    static JournalEntry with(JournalEntry e, String term, long seq, Instant at, byte[] body, byte[] prev) {
        return new JournalEntry(term, seq, at, body, prev);
    }

    static String scenario(String name, Cursor cursor, List<JournalEntry> batch) {
        ChainVerdict v = V.verify(cursor, batch);
        List<String> acc = new ArrayList<>();
        for (JournalEntry e : v.accepted()) acc.add(num(e.sequence()));
        List<String> gaps = new ArrayList<>();
        for (ChainVerdict.SequenceGap g : v.gaps()) {
            gaps.add(obj("from", num(g.fromSequence()), "to", num(g.toSequence())));
        }
        List<String> brk = new ArrayList<>();
        for (ChainVerdict.ChainBreak b : v.breaks()) {
            brk.add(obj("sequence", num(b.sequence()), "reason", q(b.reason().name()), "detail", q(b.detail())));
        }
        List<String> b = new ArrayList<>();
        for (JournalEntry e : batch) b.add(entryJson(e));
        Cursor next = V.advance(cursor, v);
        return obj("name", q(name), "cursor", cursorJson(cursor), "batch", arr(b),
                "accepted", arr(acc), "gaps", arr(gaps), "breaks", arr(brk),
                "isClean", String.valueOf(v.isClean()), "advancedCursor", cursorJson(next));
    }

    static String verifierVectors() {
        Cursor fresh = Cursor.fresh(TERM);
        List<String> sc = new ArrayList<>();

        List<JournalEntry> honest = chain(TERM, ChainDigest.GENESIS, 1, 6, T0);
        sc.add(scenario("honest-fresh", fresh, honest));

        Cursor after3 = V.advance(fresh, V.verify(fresh, honest.subList(0, 3)));
        sc.add(scenario("continues-from-cursor", after3, honest));
        Cursor afterAll = V.advance(fresh, V.verify(fresh, honest));
        sc.add(scenario("resend-of-ingested", afterAll, honest));

        List<JournalEntry> rev = new ArrayList<>(honest);
        java.util.Collections.reverse(rev);
        sc.add(scenario("arrives-reversed", fresh, rev));

        List<JournalEntry> gap = new ArrayList<>(honest);
        gap.remove(2);   // sequence 3 deleted
        sc.add(scenario("deleted-middle", fresh, gap));

        List<JournalEntry> gapWide = new ArrayList<>(honest);
        gapWide.remove(4);
        gapWide.remove(3);
        gapWide.remove(2);
        gapWide.remove(1);   // 2..5 gone, 1 and 6 remain
        sc.add(scenario("deleted-run", fresh, gapWide));

        List<JournalEntry> firstMissing = new ArrayList<>(honest.subList(1, 6));
        sc.add(scenario("first-entry-missing", fresh, firstMissing));

        List<JournalEntry> tampered = new ArrayList<>(honest);
        JournalEntry t3 = tampered.get(2);
        tampered.set(2, with(t3, TERM, 3, t3.occurredAt(), ChainDigest.body(ChainDigest.utf8("forged")),
                t3.previousDigest()));
        sc.add(scenario("edited-body-breaks-next-link", fresh, tampered));

        List<JournalEntry> relinked = new ArrayList<>(honest);
        JournalEntry r4 = relinked.get(3);
        relinked.set(3, with(r4, TERM, 4, r4.occurredAt(), r4.bodyDigest(), fill(32, 99)));
        sc.add(scenario("bad-previous-link", fresh, relinked));

        List<JournalEntry> forked = new ArrayList<>(honest.subList(0, 3));
        forked.add(with(honest.get(2), TERM, 3, honest.get(2).occurredAt().plusSeconds(1),
                ChainDigest.body(ChainDigest.utf8("second three")), honest.get(1).previousDigest()));
        forked.addAll(honest.subList(3, 6));
        sc.add(scenario("duplicate-sequence", fresh, forked));

        List<JournalEntry> restarted = chain(TERM, ChainDigest.GENESIS, 1, 3, T0.plusSeconds(3600));
        // Different content at 1 than the genesis the server holds
        sc.add(scenario("restarted-chain", afterAll, restarted));

        List<JournalEntry> backdated = new ArrayList<>();
        byte[] prev = ChainDigest.GENESIS;
        long[] secs = {100, 200, 150, 300};
        for (int i = 0; i < 4; i++) {
            JournalEntry e = new JournalEntry(TERM, i + 1, T0.plusSeconds(secs[i]),
                    ChainDigest.body(ChainDigest.utf8("b" + i)), prev);
            backdated.add(e);
            prev = ChainDigest.of(e);
        }
        sc.add(scenario("clock-runs-backwards", fresh, backdated));

        List<JournalEntry> foreign = new ArrayList<>(honest.subList(0, 2));
        foreign.add(with(honest.get(2), "TERM-OTHER", 3, honest.get(2).occurredAt(), honest.get(2).bodyDigest(),
                honest.get(2).previousDigest()));
        sc.add(scenario("entry-from-other-terminal", fresh, foreign));

        sc.add(scenario("empty-batch", fresh, List.of()));

        // Two entries swapped by sequence number (bodies stay in original order): reorder attack.
        List<JournalEntry> swapped = new ArrayList<>(honest);
        JournalEntry a = honest.get(1), b = honest.get(2);
        swapped.set(1, with(a, TERM, 3, a.occurredAt(), a.bodyDigest(), a.previousDigest()));
        swapped.set(2, with(b, TERM, 2, b.occurredAt(), b.bodyDigest(), b.previousDigest()));
        sc.add(scenario("sequence-numbers-swapped", fresh, swapped));

        return arr(sc);
    }

    // ----------------------------------------------------------------- fiscal

    static String leaseJson(FiscalLease l) {
        return obj("terminalId", q(l.terminalId()), "first", num(l.firstNumber()), "last", num(l.lastNumber()),
                "next", num(l.nextNumber()), "issuedAtMs", num(l.issuedAt().toEpochMilli()),
                "expiresAtMs", num(l.expiresAt().toEpochMilli()));
    }

    static String fiscalVectors() {
        Instant issued = Instant.parse("2026-09-29T00:00:00Z");
        Instant expires = issued.plusSeconds(86_400);
        FiscalLease lease = FiscalLease.issue(TERM, 1001, 10, issued, expires);
        List<String> steps = new ArrayList<>();
        FiscalLease cur = lease;
        for (int i = 0; i < 12; i++) {
            var d = cur.draw();
            Instant probe = issued.plusSeconds(3600);
            steps.add(obj("lease", leaseJson(cur), "remaining", num(cur.remaining()),
                    "size", num(cur.size()), "exhausted", String.valueOf(cur.isExhausted()),
                    "needsRenewal", String.valueOf(cur.needsRenewal(probe)),
                    "drawn", d.isPresent() ? num(d.get().number()) : "null",
                    "unused", cur.unusedRange().isPresent()
                            ? arr(List.of(num(cur.unusedRange().get()[0]), num(cur.unusedRange().get()[1])))
                            : "null"));
            if (d.isPresent()) cur = d.get().remainder();
        }
        List<String> expiry = new ArrayList<>();
        for (long off : new long[] {0, 86_399, 86_400, 90_000}) {
            expiry.add(obj("atMs", num(issued.plusSeconds(off).toEpochMilli()),
                    "expired", String.valueOf(lease.isExpired(issued.plusSeconds(off))),
                    "needsRenewal", String.valueOf(lease.needsRenewal(issued.plusSeconds(off)))));
        }
        return obj("steps", arr(steps), "expiry", arr(expiry));
    }

    // -------------------------------------------------------------- signature

    static String signatureVectors() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("Ed25519");
        List<String> out = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            KeyPair kp = g.generateKeyPair();
            byte[] msg = ChainDigest.body(ChainDigest.utf8("signed-" + i));
            Signature s = Signature.getInstance("Ed25519");
            s.initSign(kp.getPrivate());
            s.update(msg);
            byte[] sig = s.sign();
            String spki = Base64.getEncoder().encodeToString(kp.getPublic().getEncoded());
            out.add(obj("publicKeySpkiBase64", q(spki), "message", hex(msg), "signature", hex(sig),
                    "javaVerifies", String.valueOf(TerminalSignature.verify(spki, msg, sig))));
        }
        return arr(out);
    }

    public static void main(String[] args) throws Exception {
        String json = obj(
                "generatedBy", q("platform classes: Money, ChainDigest, JournalVerifier, FiscalLease, TerminalSignature"),
                "money", moneyVectors(),
                "digest", digestVectors(),
                "verifier", verifierVectors(),
                "fiscal", fiscalVectors(),
                "signatures", signatureVectors());
        Files.writeString(Path.of(args[0]), json + "\n", StandardCharsets.UTF_8);
        System.out.println("wrote " + args[0] + " (" + json.length() + " chars)");

        if (args.length > 1) {
            String sample = Files.readString(Path.of(args[1]));
            String spki = field(sample, "publicKeySpkiBase64");
            byte[] msg = ChainDigest.fromHex(field(sample, "message"));
            byte[] sig = ChainDigest.fromHex(field(sample, "signature"));
            System.out.println("WebCrypto signature verified by TerminalSignature.verify: "
                    + TerminalSignature.verify(spki, msg, sig));
            byte[] tampered = msg.clone();
            tampered[0] ^= 1;
            System.out.println("same signature over altered message verifies: "
                    + TerminalSignature.verify(spki, tampered, sig));
        }
    }

    static String field(String json, String key) {
        int i = json.indexOf("\"" + key + "\"");
        int a = json.indexOf('"', json.indexOf(':', i) + 1) + 1;
        return json.substring(a, json.indexOf('"', a));
    }
}
