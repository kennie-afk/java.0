import { describe, expect, it } from "vitest";
import { decideFiscal, draw, type FiscalLease, needsRenewal, isExpired, remaining, size, unusedRange, isExhausted } from "../src/lib/fiscal";
import { vectors } from "./vectors";

const leaseOf = (v: any): FiscalLease => ({
  terminalId: v.terminalId,
  firstNumber: BigInt(v.first),
  lastNumber: BigInt(v.last),
  nextNumber: BigInt(v.next),
  issuedAtMs: Number(v.issuedAtMs),
  expiresAtMs: Number(v.expiresAtMs)
});

describe("fiscal lease matches the Java FiscalLease", () => {
  it("draw / remaining / renewal / unused range at every step to exhaustion", () => {
    const probe = vectors.fiscal.steps[0] && Number(vectors.fiscal.steps[0].lease.issuedAtMs) + 3_600_000;
    for (const step of vectors.fiscal.steps) {
      const l = leaseOf(step.lease);
      expect(remaining(l).toString()).toBe(step.remaining);
      expect(size(l).toString()).toBe(step.size);
      expect(isExhausted(l)).toBe(step.exhausted === true);
      expect(needsRenewal(l, probe)).toBe(step.needsRenewal === true);
      const d = draw(l);
      expect(d ? d.number.toString() : null).toBe(step.drawn);
      const u = unusedRange(l);
      expect(u ? u.map(String) : null).toEqual(step.unused);
    }
  });

  it("expiry", () => {
    const l = leaseOf(vectors.fiscal.steps[0].lease);
    for (const e of vectors.fiscal.expiry) {
      expect(isExpired(l, Number(e.atMs))).toBe(e.expired === true);
      expect(needsRenewal(l, Number(e.atMs))).toBe(e.needsRenewal === true);
    }
  });

  it("no lease means FISCAL_PENDING, never an invented number", () => {
    const d = decideFiscal(null, Date.now());
    expect(d).toMatchObject({ status: "FISCAL_PENDING", number: null, reason: "NO_LEASE" });
  });

  it("a live lease numbers the sale and an exhausted or expired one degrades to pending", () => {
    const l = leaseOf(vectors.fiscal.steps[0].lease);
    const now = l.issuedAtMs + 1000;
    const numbered = decideFiscal(l, now);
    expect(numbered.status).toBe("NUMBERED");
    expect(numbered.number).toBe(l.firstNumber);
    expect(numbered.lease!.nextNumber).toBe(l.firstNumber + 1n);
    expect(decideFiscal({ ...l, nextNumber: l.lastNumber + 1n }, now).reason).toBe("LEASE_EXHAUSTED");
    expect(decideFiscal(l, l.expiresAtMs).reason).toBe("LEASE_EXPIRED");
  });
});
