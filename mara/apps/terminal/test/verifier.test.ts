import { describe, expect, it } from "vitest";
import { fromHex, toHex } from "../src/lib/bytes";
import { advance, type Cursor, verify } from "../src/lib/verifier";
import { vectors } from "./vectors";

const entryOf = (v: any) => ({
  terminalId: v.terminalId as string,
  sequence: BigInt(v.sequence),
  epochSecond: BigInt(v.epochSecond),
  nano: Number(v.nano),
  bodyDigest: fromHex(v.bodyDigest),
  previousDigest: fromHex(v.previousDigest)
});
const cursorOf = (c: any): Cursor => ({
  terminalId: c.terminalId,
  lastSequence: BigInt(c.lastSequence),
  headDigest: fromHex(c.headDigest),
  genesisDigest: fromHex(c.genesisDigest),
  lastEpochSecond: BigInt(c.lastEpochSecond),
  lastNano: Number(c.lastNano)
});

describe("verifier reproduces the Java JournalVerifier verdicts", () => {
  const scenarios: any[] = vectors.verifier;

  it("covers the attack shapes: tamper, deletion gap, reorder, fork, restart, backdating", () => {
    const names = scenarios.map((s) => s.name);
    for (const n of [
      "deleted-middle", "deleted-run", "first-entry-missing", "edited-body-breaks-next-link", "bad-previous-link",
      "duplicate-sequence", "restarted-chain", "clock-runs-backwards", "sequence-numbers-swapped", "arrives-reversed"
    ]) {
      expect(names).toContain(n);
    }
  });

  for (const s of scenarios) {
    it(`${s.name}`, async () => {
      const cursor = cursorOf(s.cursor);
      const verdict = await verify(cursor, s.batch.map(entryOf));
      expect(verdict.accepted.map((e) => e.sequence.toString())).toEqual(s.accepted);
      expect(verdict.gaps.map((g) => ({ from: g.fromSequence.toString(), to: g.toSequence.toString() }))).toEqual(s.gaps);
      expect(verdict.breaks.map((b) => ({ sequence: b.sequence.toString(), reason: b.reason }))).toEqual(
        s.breaks.map((b: any) => ({ sequence: b.sequence, reason: b.reason }))
      );
      const next = await advance(cursor, verdict);
      const want = cursorOf(s.advancedCursor);
      expect(next.lastSequence).toBe(want.lastSequence);
      expect(toHex(next.headDigest)).toBe(toHex(want.headDigest));
      expect(toHex(next.genesisDigest)).toBe(toHex(want.genesisDigest));
      expect(next.lastEpochSecond).toBe(want.lastEpochSecond);
      expect(next.lastNano).toBe(want.lastNano);
    });
  }
});
