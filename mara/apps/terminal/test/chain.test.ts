import { describe, expect, it } from "vitest";
import { fromBase64, fromHex, toHex, utf8 } from "../src/lib/bytes";
import { bodyDigest, chainDigest, GENESIS, instantFromMillis } from "../src/lib/chain";
import { generateTerminalKey, signDigest, verifyDigest } from "../src/lib/keys";
import { bodyDigestFromFields, buildSaleBody, canonicalLines, saleBodyDigest } from "../src/lib/sale";
import { vectors } from "./vectors";

const entryOf = (v: any) => ({
  terminalId: v.terminalId as string,
  sequence: BigInt(v.sequence),
  epochSecond: BigInt(v.epochSecond),
  nano: Number(v.nano),
  bodyDigest: fromHex(v.bodyDigest),
  previousDigest: fromHex(v.previousDigest)
});

describe("chain digest is byte-for-byte the Java ChainDigest", () => {
  it("genesis is 32 zero bytes", () => {
    expect(toHex(GENESIS)).toBe(vectors.digest.genesis);
  });

  it("entry digests: unicode ids, nanos, sequences up to Long.MAX_VALUE", async () => {
    expect(vectors.digest.entries.length).toBeGreaterThanOrEqual(35);
    for (const v of vectors.digest.entries) {
      expect(toHex(await chainDigest(entryOf(v))), `${v.terminalId} #${v.sequence}`).toBe(v.digest);
    }
  });

  it("body digests over raw length-prefixed fields", async () => {
    for (const v of vectors.digest.rawBodies) {
      expect(toHex(await bodyDigest(...v.fields.map(fromHex)))).toBe(v.digest);
    }
  });

  it("the sale body digest, over the terminal's field list, incl. non-ASCII", async () => {
    for (const v of vectors.digest.saleBodies) {
      const got = await bodyDigestFromFields(v.version, v.currency, v.lines, v.payments, BigInt(v.total), v.fiscal);
      expect(toHex(got)).toBe(v.bodyDigest);
    }
  });

  it("a whole 25-entry chain built by Java is reproduced link by link", async () => {
    let prev = GENESIS;
    for (const v of vectors.digest.chain) {
      const e = entryOf({ ...v, terminalId: "TERM-CHAIN" });
      expect(toHex(e.previousDigest)).toBe(toHex(prev));
      prev = await chainDigest(e);
      expect(toHex(prev)).toBe(v.digest);
    }
  });

  it("length prefixes make shifted bytes produce different digests", async () => {
    const a = await bodyDigest(new Uint8Array([1, 2]), new Uint8Array([3]));
    const b = await bodyDigest(new Uint8Array([1]), new Uint8Array([2, 3]));
    expect(toHex(a)).not.toBe(toHex(b));
  });

  it("millisecond clock splits into second and nano exactly", () => {
    expect(instantFromMillis(1_788_000_123_456)).toEqual({ epochSecond: 1_788_000_123n, nano: 456_000_000 });
    expect(instantFromMillis(999)).toEqual({ epochSecond: 0n, nano: 999_000_000 });
  });
});

describe("sale body encoding", () => {
  it("is deterministic and changes with every committed field", async () => {
    const totals = {
      lines: [{ sku: "A", name: "Unga", unitMinor: 14500n, taxBp: 0, qty: 2, netMinor: 29000n, taxMinor: 0n }],
      net: { minor: 29000n, currency: "KES" },
      tax: { minor: 0n, currency: "KES" },
      total: { minor: 29000n, currency: "KES" }
    };
    const pay = [{ method: "CASH" as const, appliedMinor: 29000n, tenderedMinor: 30000n, reference: "" }];
    const base = buildSaleBody("KES", totals, pay, { status: "FISCAL_PENDING", number: null });
    const d0 = toHex(await saleBodyDigest(base));
    expect(toHex(await saleBodyDigest(structuredClone(base)))).toBe(d0);
    expect(canonicalLines(base)).toBe('[["A","Unga","2","14500","0","29000","0"]]');

    const edits: ((b: typeof base) => void)[] = [
      (b) => (b.lines[0].qty = 3),
      (b) => (b.lines[0].name = "Unga "),
      (b) => (b.lines[0].unitMinor = "14501"),
      (b) => (b.payments[0].reference = "x"),
      (b) => (b.payments[0].tenderedMinor = "30001"),
      (b) => (b.totalMinor = "29001"),
      (b) => (b.currency = "USD"),
      (b) => (b.fiscal = { status: "NUMBERED", number: "1" })
    ];
    for (const edit of edits) {
      const b = structuredClone(base);
      edit(b);
      expect(toHex(await saleBodyDigest(b))).not.toBe(d0);
    }
  });
});

describe("Ed25519 signatures interoperate with the platform's TerminalSignature", () => {
  it("signatures made by Java verify in WebCrypto (SPKI base64 as identity-service stores it)", async () => {
    expect(vectors.signatures.length).toBeGreaterThan(0);
    for (const v of vectors.signatures) {
      expect(v.javaVerifies).toBe(true);
      const digest = fromHex(v.message);
      expect(await verifyDigest(v.publicKeySpkiBase64, digest, v.signature)).toBe(true);
      const altered = new Uint8Array(digest);
      altered[0] ^= 1;
      expect(await verifyDigest(v.publicKeySpkiBase64, altered, v.signature)).toBe(false);
    }
  });

  it("the generated key is non-extractable, exports a 44-byte SPKI, and signs/verifies", async () => {
    const key = await generateTerminalKey();
    expect(key.privateKey.extractable).toBe(false);
    await expect(crypto.subtle.exportKey("pkcs8", key.privateKey)).rejects.toThrow();
    expect(fromBase64(key.publicKeySpkiBase64).length).toBe(44);
    const d = await bodyDigest(utf8("hello"));
    const sig = await signDigest(key.privateKey, d);
    expect(sig.length).toBe(128);
    expect(await verifyDigest(key.publicKeySpkiBase64, d, sig)).toBe(true);
    expect(await verifyDigest(key.publicKeySpkiBase64, await bodyDigest(utf8("other")), sig)).toBe(false);
  });
});
