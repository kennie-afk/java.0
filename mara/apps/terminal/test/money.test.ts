import { describe, expect, it } from "vitest";
import {
  allocate, CURRENCY_DIGITS, format, money, MoneyOverflowError, minus, negated, parseMajor, percentage, plus, times
} from "../src/lib/money";
import { vectors } from "./vectors";

const M = (minor: string | bigint | number, c = "KES") => money(BigInt(minor), c);
const kind = (fn: () => unknown) => {
  try {
    fn();
    return "none";
  } catch (e) {
    return e instanceof MoneyOverflowError ? "arithmetic" : e instanceof RangeError ? "illegalArgument" : "other";
  }
};

describe("Money matches the Java implementation", () => {
  it("allocate: every vector Java produced", () => {
    expect(vectors.money.allocate.length).toBeGreaterThan(100);
    for (const v of vectors.money.allocate) {
      const got = allocate(M(v.minor), v.parts).map((m) => m.minor.toString());
      expect(got, `allocate(${v.minor}, ${v.parts})`).toEqual(v.result);
    }
  });

  it("allocate: rejects non-positive part counts like Java", () => {
    for (const v of vectors.money.allocateErrors) {
      expect(v.throws).toBe(true);
      expect(() => allocate(M(10), Number(v.parts))).toThrow();
    }
  });

  it("percentage: every vector Java produced (half-up, symmetric for negatives)", () => {
    expect(vectors.money.percentage.length).toBeGreaterThan(200);
    for (const v of vectors.money.percentage) {
      expect(percentage(M(v.minor), BigInt(v.bp)).minor.toString(), `${v.minor} @ ${v.bp}bp`).toBe(v.result);
    }
  });

  it("percentage and arithmetic overflow/negative behaviour matches Java's exact-math exceptions", () => {
    for (const v of vectors.money.percentageErrors) {
      expect(kind(() => percentage(M(v.minor), BigInt(v.bp)))).toBe(v.kind);
    }
    for (const v of vectors.money.arithmetic) {
      const a = M(v.a);
      const b = M(v.b);
      const run = { plus: () => plus(a, b), minus: () => minus(a, b), times: () => times(a, BigInt(v.b)), negate: () => negated(a) }[
        v.op as "plus" | "minus" | "times" | "negate"
      ];
      expect(kind(run)).toBe(v.kind === "ok" ? "none" : v.kind);
      if (v.kind === "ok") expect(run().minor.toString()).toBe(v.result);
    }
  });

  it("format: identical text for every currency and amount, and the digits table agrees with java.util.Currency", () => {
    for (const v of vectors.money.format) {
      expect(CURRENCY_DIGITS[v.currency], v.currency).toBe(v.digits);
      expect(format(M(v.minor, v.currency)), `${v.minor} ${v.currency}`).toBe(v.text);
    }
  });
});

describe("Money invariants", () => {
  it("a split bill always sums to the original, for many amounts and share counts", () => {
    let seed = 12345n;
    const next = () => (seed = (seed * 6364136223846793005n + 1442695040888963407n) & ((1n << 62n) - 1n));
    for (let i = 0; i < 2000; i++) {
      const amount = (next() % 2_000_000_000_000n) - 1_000_000_000_000n;
      const parts = Number(next() % 60n) + 1;
      const shares = allocate(M(amount), parts);
      expect(shares.reduce((n, m) => n + m.minor, 0n)).toBe(amount);
      const min = shares.reduce((n, m) => (m.minor < n ? m.minor : n), shares[0].minor);
      const max = shares.reduce((n, m) => (m.minor > n ? m.minor : n), shares[0].minor);
      expect(max - min <= 1n).toBe(true);
    }
  });

  it("classic float traps stay exact", () => {
    expect(plus(M(10), M(20)).minor).toBe(30n);
    expect(percentage(M(1999), 1600n).minor).toBe(320n);   // 319.84 rounds half-up
    expect(percentage(M(3125), 1600n).minor).toBe(500n);
  });

  it("parseMajor is exact and strict", () => {
    expect(parseMajor("1234.5", "KES")).toBe(123450n);
    expect(parseMajor("1,234.50", "KES")).toBe(123450n);
    expect(parseMajor("0.07", "KES")).toBe(7n);
    expect(parseMajor("5", "UGX")).toBe(5n);
    expect(parseMajor("5.5", "UGX")).toBeNull();
    expect(parseMajor("1.234", "KES")).toBeNull();
    expect(parseMajor("-1", "KES")).toBeNull();
    expect(parseMajor("abc", "KES")).toBeNull();
    expect(parseMajor("", "KES")).toBeNull();
    expect(parseMajor("9223372036854775807", "UGX")).toBe(9223372036854775807n);
    expect(parseMajor("9223372036854775808", "UGX")).toBeNull();
  });
});
