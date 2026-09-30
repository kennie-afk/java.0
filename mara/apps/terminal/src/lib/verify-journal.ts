/**
 * Local journal verification: the Java JournalVerifier's semantics, run over the whole
 * stored chain in pages (never loading it all), plus the extra checks only the device
 * itself can make because it holds the sale bodies and its own public key.
 *
 * The report has NO single boolean. It lists, separately: how many entries verified, any
 * sequence gaps, and any breaks (chain, body, digest, signature, head). The UI turns
 * those into "intact", "broken at N" and "gap at N..M".
 *
 * Scope, stated plainly: this proves the journal is internally consistent and that the
 * entries were signed by this device's key. It cannot prove nothing was deleted from the
 * end AND from the head record together, and it cannot prove anything to a third party:
 * nothing has been sent to a server that could hold an independent copy.
 */
import { fromHex, toHex } from "./bytes";
import { type ChainEntry, chainDigest } from "./chain";
import { advance, type BreakReason, freshCursor, verify } from "./verifier";
import { verifyDigest } from "./keys";
import type { JournalHead, JournalRecord } from "./records";
import { saleBodyDigest } from "./sale";

export type LocalBreakReason =
  | BreakReason
  | "BODY_MISMATCH"
  | "DIGEST_MISMATCH"
  | "BAD_SIGNATURE"
  | "HEAD_MISMATCH";

export interface LocalReport {
  /** entries that passed every check, counted from sequence 1 */
  verifiedThrough: number;
  entriesChecked: number;
  gaps: { fromSequence: number; toSequence: number; where: "inside" | "tail" }[];
  breaks: { sequence: number; reason: LocalBreakReason; detail: string }[];
  /** true when the scan stopped at its first problem rather than reaching the end */
  stoppedEarly: boolean;
}

export interface VerifySource {
  head(): Promise<JournalHead | null>;
  page(afterSequence: number, limit: number): Promise<JournalRecord[]>;
}

export const toChainEntry = (r: JournalRecord): ChainEntry => ({
  terminalId: r.terminalId,
  sequence: BigInt(r.sequence),
  epochSecond: BigInt(r.epochSecond),
  nano: r.nano,
  bodyDigest: fromHex(r.bodyDigest),
  previousDigest: fromHex(r.previousDigest)
});

export async function verifyLocalJournal(args: {
  terminalId: string;
  publicKeySpkiBase64: string;
  source: VerifySource;
  pageSize?: number;
  onProgress?: (checked: number) => void;
}): Promise<LocalReport> {
  const pageSize = args.pageSize ?? 500;
  const report: LocalReport = { verifiedThrough: 0, entriesChecked: 0, gaps: [], breaks: [], stoppedEarly: false };
  let cursor = freshCursor(args.terminalId);
  let after = 0;
  let stop = false;

  while (!stop) {
    const rows = await args.source.page(after, pageSize);
    if (rows.length === 0) break;
    after = rows[rows.length - 1].sequence;

    const byEntry = new Map<number, JournalRecord>(rows.map((r) => [r.sequence, r]));
    const verdict = await verify(cursor, rows.map(toChainEntry));

    // Everything the chain accepted is checked further, in sequence order, so the first
    // problem reported is the first in the journal.
    for (const entry of verdict.accepted) {
      const r = byEntry.get(Number(entry.sequence))!;
      const problem = await localProblem(r, entry, args.publicKeySpkiBase64);
      if (problem) {
        report.breaks.push({ sequence: r.sequence, ...problem });
        stop = true;
        break;
      }
      report.verifiedThrough = r.sequence;
      report.entriesChecked++;
    }
    if (stop) break;

    for (const g of verdict.gaps) {
      report.gaps.push({ fromSequence: Number(g.fromSequence), toSequence: Number(g.toSequence), where: "inside" });
      stop = true;
    }
    for (const b of verdict.breaks) {
      report.breaks.push({ sequence: Number(b.sequence), reason: b.reason, detail: b.detail });
      stop = true;
    }
    if (stop) break;

    cursor = await advance(cursor, verdict);
    args.onProgress?.(report.entriesChecked);
    if (rows.length < pageSize) break;
  }

  report.stoppedEarly = stop;

  if (!stop) {
    // Reached the end of what is stored. Compare with the head record kept beside it.
    const head = await args.source.head();
    const last = report.verifiedThrough;
    if (head) {
      if (head.lastSequence > last) {
        report.gaps.push({ fromSequence: last + 1, toSequence: head.lastSequence, where: "tail" });
      } else if (head.lastSequence < last || (last > 0 && head.headDigest !== toHex(cursor.headDigest))) {
        report.breaks.push({
          sequence: last,
          reason: "HEAD_MISMATCH",
          detail: "the stored head record does not match the last entry in the journal"
        });
      }
    } else if (last > 0) {
      report.breaks.push({
        sequence: last,
        reason: "HEAD_MISMATCH",
        detail: "entries exist but the head record is missing"
      });
    }
  }
  return report;
}

async function localProblem(
  r: JournalRecord,
  entry: ChainEntry,
  publicKeySpkiBase64: string
): Promise<{ reason: LocalBreakReason; detail: string } | null> {
  const body = toHex(await saleBodyDigest(r.sale));
  if (body !== r.bodyDigest) {
    return { reason: "BODY_MISMATCH", detail: "the sale content no longer matches the digest recorded for it" };
  }
  const digest = await chainDigest(entry);
  if (toHex(digest) !== r.digest) {
    return { reason: "DIGEST_MISMATCH", detail: "the stored entry digest does not match its own fields" };
  }
  if (!(await verifyDigest(publicKeySpkiBase64, digest, r.signature))) {
    return { reason: "BAD_SIGNATURE", detail: "the entry's signature does not verify against this terminal's key" };
  }
  return null;
}

export type Finding =
  | { kind: "intact"; entries: number }
  | { kind: "empty" }
  | { kind: "broken"; sequence: number; reason: LocalBreakReason; detail: string }
  | { kind: "gap"; fromSequence: number; toSequence: number; where: "inside" | "tail" };

/** The report as an ordered list of findings; never collapsed into pass/fail. */
export function findings(report: LocalReport): Finding[] {
  const out: Finding[] = [];
  for (const b of report.breaks) out.push({ kind: "broken", ...b });
  for (const g of report.gaps) out.push({ kind: "gap", ...g });
  if (out.length === 0) {
    out.push(report.entriesChecked === 0 ? { kind: "empty" } : { kind: "intact", entries: report.entriesChecked });
  }
  return out.sort((a, b) => firstSeq(a) - firstSeq(b));
}

function firstSeq(f: Finding): number {
  if (f.kind === "broken") return f.sequence;
  if (f.kind === "gap") return f.fromSequence;
  return 0;
}
