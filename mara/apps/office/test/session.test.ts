import { describe, expect, it } from "vitest";
import { createHash } from "node:crypto";
import { open, seal, sessionKey } from "../src/lib/session";

const key = sessionKey("a".repeat(40));
const other = sessionKey("b".repeat(40));
const credential = "mop_0123456789abcdef." + "A".repeat(43);

describe("the sealed sign-in cookie", () => {
  it("round-trips and does not contain the credential in the clear", () => {
    const token = seal({ credential, tenant: "TEN-1", exp: 2_000_000 }, key);
    expect(token).not.toContain(credential);
    expect(Buffer.from(token, "base64url").toString("latin1")).not.toContain("mop_");
    expect(open(token, 1_000_000, key)).toEqual({ credential, tenant: "TEN-1", exp: 2_000_000 });
  });

  it("is refused after it expires, under another key, or if a single byte is changed", () => {
    const token = seal({ credential, tenant: null, exp: 2_000_000 }, key);
    expect(open(token, 2_000_000, key)).toBeNull();
    expect(open(token, 3_000_000, key)).toBeNull();
    expect(open(token, 1_000_000, other)).toBeNull();
    const raw = Buffer.from(token, "base64url");
    for (const i of [0, 12, 20, raw.length - 1]) {
      const copy = Buffer.from(raw);
      copy[i] ^= 1;
      expect(open(copy.toString("base64url"), 1_000_000, key), `byte ${i}`).toBeNull();
    }
  });

  it("refuses garbage, an empty value and an oversized one without throwing", () => {
    for (const bad of [undefined, "", "x", "not base64 !!", "A".repeat(10), "A".repeat(5000)]) {
      expect(open(bad, 1, key)).toBeNull();
    }
  });

  it("two seals of the same session differ (fresh nonce each time)", () => {
    const s = { credential, tenant: null, exp: 2_000_000 };
    expect(seal(s, key)).not.toBe(seal(s, key));
  });

  it("will not derive a key from a short or missing secret", () => {
    expect(() => sessionKey("short")).toThrow(/32 characters/);
    expect(() => sessionKey(undefined)).toThrow(/32 characters/);
    expect(sessionKey("c".repeat(32))).toEqual(createHash("sha256").update("c".repeat(32)).digest());
  });
});
