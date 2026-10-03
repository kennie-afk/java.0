import { describe, expect, it } from "vitest";
import { addDays, isoDay, money } from "../src/lib/format";
import { clientKey, Limiter } from "../src/lib/limit";

describe("sign-in limiter", () => {
  it("allows the limit, refuses beyond it, and reopens after the window", () => {
    const l = new Limiter(3, 1000);
    expect([1, 2, 3, 4].map(() => l.take("a", 0))).toEqual([true, true, true, false]);
    expect(l.take("b", 0)).toBe(true);
    expect(l.take("a", 999)).toBe(false);
    expect(l.take("a", 1000)).toBe(true);
  });

  it("stays bounded when many distinct keys arrive", () => {
    const l = new Limiter(1, 1000, 5);
    for (let i = 0; i < 100; i++) l.take("k" + i, 0);
    expect(l.take("fresh", 0)).toBe(false);   // spilled into the shared overflow bucket, already used
  });

  it("counts the address the ingress appended, never an arbitrary string", () => {
    expect(clientKey("6.6.6.6, 203.0.113.9")).toBe("203.0.113.9");
    expect(clientKey("<script>")).toBe("unknown");
    expect(clientKey(null)).toBe("unknown");
  });
});

describe("money and dates", () => {
  it("formats minor units exactly, including large and negative amounts", () => {
    expect(money(0)).toBe("KES 0.00");
    expect(money(5)).toBe("KES 0.05");
    expect(money(123456789)).toBe("KES 1,234,567.89");
    expect(money("9007199254740993")).toBe("KES 90,071,992,547,409.93");   // beyond 2^53, still exact
    expect(money(-1050)).toBe("-KES 10.50");
  });

  it("works out the shop's local day, not the server's", () => {
    // 20:30 UTC on the 9th is 23:30 in Nairobi (still the 9th); 21:30 UTC is 00:30 on the 10th
    expect(isoDay(new Date("2026-10-09T20:30:00Z"), "Africa/Nairobi")).toBe("2026-10-09");
    expect(isoDay(new Date("2026-10-09T21:30:00Z"), "Africa/Nairobi")).toBe("2026-10-10");
    expect(addDays("2026-10-01", -1)).toBe("2026-09-30");
    expect(addDays("2026-12-31", 1)).toBe("2027-01-01");
  });
});
