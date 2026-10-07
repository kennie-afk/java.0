import { describe, expect, it } from "vitest";
import { adjustmentProblem, duplicateCheckBody, mergeReady, passwordProblem, permitted, rescheduleBody } from "./rules";

describe("merge confirmation", () => {
  const base = { typed: "MERGE", reason: "Same person registered twice", duplicateId: "a", survivorId: "b" };
  it("is ready only with the word, a real reason and a different survivor", () => {
    expect(mergeReady(base)).toBe(true);
    expect(mergeReady({ ...base, typed: "merge" })).toBe(false);
    expect(mergeReady({ ...base, reason: "short" })).toBe(false);
    expect(mergeReady({ ...base, survivorId: "a" })).toBe(false);
    expect(mergeReady({ ...base, survivorId: null })).toBe(false);
  });
});

describe("password change", () => {
  it("reports the first problem", () => {
    expect(passwordProblem("", "x", "x")).toMatch(/current/);
    expect(passwordProblem("old-password-1", "short", "short")).toMatch(/12/);
    expect(passwordProblem("old-password-1", "old-password-1", "old-password-1")).toMatch(/differ/);
    expect(passwordProblem("old-password-1", "new-password-123", "other-password-1")).toMatch(/match/);
    expect(passwordProblem("old-password-1", "new-password-123", "new-password-123")).toBeNull();
  });
});

describe("stock adjustment", () => {
  const ok = { delta: -2, reason: "ADJUSTMENT" as const, note: "recount", onHand: 10 };
  it("mirrors the server rules", () => {
    expect(adjustmentProblem(ok)).toBeNull();
    expect(adjustmentProblem({ ...ok, delta: 0 })).toMatch(/changes/);
    expect(adjustmentProblem({ ...ok, delta: Number.NaN })).toMatch(/changes/);
    expect(adjustmentProblem({ ...ok, reason: "WRITE_OFF", delta: 3 })).toMatch(/reduces/);
    expect(adjustmentProblem({ ...ok, delta: -11 })).toMatch(/below zero/);
    expect(adjustmentProblem({ ...ok, note: "no" })).toMatch(/5 characters/);
  });
});

describe("request builders", () => {
  it("builds the reschedule body with the version seen", () => {
    expect(rescheduleBody("2026-10-07T07:00:00Z", 3)).toEqual({ startsAt: "2026-10-07T07:00:00Z", version: 3 });
  });
  it("leaves the MRN out of the duplicate check and drops empty optionals", () => {
    const b = duplicateCheckBody({ givenName: "A", familyName: "B", sex: "FEMALE", birthDate: "1990-01-01", phone: "", identifiers: [{ system: "MRN", value: "1" }, { system: "NATIONAL_ID", value: "22" }] });
    expect(b.identifiers).toEqual([{ system: "NATIONAL_ID", value: "22" }]);
    expect(b.demographics.phone).toBeUndefined();
  });
});

describe("permission gating", () => {
  it("shows an item only if the server granted it, or none is required", () => {
    expect(permitted(["patients:read"], "")).toBe(true);
    expect(permitted(["patients:read"], "patients:read")).toBe(true);
    expect(permitted(["patients:read"], "patients:merge")).toBe(false);
    expect(permitted([], "patients:read")).toBe(false);
  });
});
