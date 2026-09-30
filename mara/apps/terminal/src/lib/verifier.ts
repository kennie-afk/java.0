/**
 * Port of com.mara.platform.journal.JournalVerifier and ChainVerdict.
 *
 * Deliberately no boolean verdict. A batch can be perfectly chained and still be missing
 * a sequence; collapsing that to "valid: true/false" would either reject good sales or
 * hide a deleted one. The result carries accepted entries, sequence gaps and chain
 * breaks separately, exactly as the Java record does.
 *
 * The control flow follows the Java method statement for statement (including which
 * conditions `break` out of the batch) so the two can be compared on the same inputs.
 */
import { type ChainEntry, chainDigest, digestsEqual, GENESIS, isGenesis } from "./chain";

export type BreakReason =
  | "BROKEN_LINK"
  | "FORKED_SEQUENCE"
  | "OUT_OF_ORDER"
  | "UNEXPECTED_GENESIS"
  | "NON_MONOTONIC_CLOCK";

export interface SequenceGap {
  terminalId: string;
  fromSequence: bigint;
  toSequence: bigint;
}

export interface ChainBreak {
  terminalId: string;
  sequence: bigint;
  reason: BreakReason;
  detail: string;
}

export interface ChainVerdict {
  accepted: ChainEntry[];
  gaps: SequenceGap[];
  breaks: ChainBreak[];
}

export function isClean(verdict: ChainVerdict): boolean {
  return verdict.gaps.length === 0 && verdict.breaks.length === 0;
}

export interface Cursor {
  terminalId: string;
  lastSequence: bigint;
  headDigest: Uint8Array;
  genesisDigest: Uint8Array;
  lastEpochSecond: bigint;
  lastNano: number;
}

export function freshCursor(terminalId: string): Cursor {
  return {
    terminalId,
    lastSequence: 0n,
    headDigest: GENESIS,
    genesisDigest: GENESIS,
    lastEpochSecond: 0n,
    lastNano: 0
  };
}

function isFresh(cursor: Cursor): boolean {
  return cursor.lastSequence === 0n;
}

/** Instant ordering on (epochSecond, nano). */
function before(aSec: bigint, aNano: number, bSec: bigint, bNano: number): boolean {
  return aSec < bSec || (aSec === bSec && aNano < bNano);
}

const hexOf = (b: Uint8Array) => Array.from(b, (x) => x.toString(16).padStart(2, "0")).join("");

export async function verify(cursor: Cursor, batch: ChainEntry[]): Promise<ChainVerdict> {
  const accepted: ChainEntry[] = [];
  const gaps: SequenceGap[] = [];
  const breaks: ChainBreak[] = [];

  if (batch.length === 0) {
    return { accepted, gaps, breaks };
  }

  // Sorted by sequence so a merely out-of-order upload is not mistaken for tampering.
  // Array.prototype.sort is stable, like the Java stream sort.
  const ordered = [...batch].sort((a, b) => (a.sequence < b.sequence ? -1 : a.sequence > b.sequence ? 1 : 0));

  const terminalId = cursor.terminalId;
  for (const entry of ordered) {
    if (terminalId !== entry.terminalId) {
      breaks.push({
        terminalId,
        sequence: entry.sequence,
        reason: "OUT_OF_ORDER",
        detail: `entry belongs to terminal ${entry.terminalId}`
      });
      return { accepted: [], gaps, breaks };
    }
  }

  let expected = cursor.lastSequence + 1n;
  let previousDigest = cursor.headDigest;
  let previousSec = cursor.lastEpochSecond;
  let previousNano = cursor.lastNano;

  for (const entry of ordered) {
    const sequence = entry.sequence;

    // Entry 1 when we already hold a chain: honest resend or abandoned history. Compared
    // against the genesis digest already accepted, before the already-ingested skip.
    if (isGenesis(entry) && !isFresh(cursor)) {
      if (!digestsEqual(await chainDigest(entry), cursor.genesisDigest)) {
        breaks.push({
          terminalId,
          sequence,
          reason: "UNEXPECTED_GENESIS",
          detail:
            "terminal restarted its chain at 1 with different content; " +
            `server holds up to ${cursor.lastSequence}`
        });
        break;
      }
    }

    if (sequence <= cursor.lastSequence) {
      continue;
    }

    if (sequence > expected) {
      gaps.push({ terminalId, fromSequence: expected, toSequence: sequence - 1n });
      break;
    }

    if (sequence < expected) {
      breaks.push({
        terminalId,
        sequence,
        reason: "FORKED_SEQUENCE",
        detail: `sequence ${sequence} submitted twice within one batch`
      });
      break;
    }

    if (!digestsEqual(entry.previousDigest, previousDigest)) {
      breaks.push({
        terminalId,
        sequence,
        reason: "BROKEN_LINK",
        detail: `expected previous digest ${hexOf(previousDigest)}, got ${hexOf(entry.previousDigest)}`
      });
      break;
    }

    if (before(entry.epochSecond, entry.nano, previousSec, previousNano)) {
      breaks.push({
        terminalId,
        sequence,
        reason: "NON_MONOTONIC_CLOCK",
        detail: "occurred before the previous entry"
      });
      break;
    }

    accepted.push(entry);
    previousDigest = await chainDigest(entry);
    previousSec = entry.epochSecond;
    previousNano = entry.nano;
    expected++;
  }

  return { accepted, gaps, breaks };
}

/** The cursor to persist after ingesting `verdict.accepted`. */
export async function advance(cursor: Cursor, verdict: ChainVerdict): Promise<Cursor> {
  const accepted = verdict.accepted;
  if (accepted.length === 0) {
    return cursor;
  }
  const last = accepted[accepted.length - 1];

  let genesisDigest = cursor.genesisDigest;
  if (isFresh(cursor)) {
    const first = accepted[0];
    if (isGenesis(first)) {
      genesisDigest = await chainDigest(first);
    }
  }

  return {
    terminalId: cursor.terminalId,
    lastSequence: last.sequence,
    headDigest: await chainDigest(last),
    genesisDigest,
    lastEpochSecond: last.epochSecond,
    lastNano: last.nano
  };
}
