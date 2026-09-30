/**
 * Turns a tab plus tenders into a signed, chained journal entry.
 *
 * Order of operations, all under the cross-tab lock:
 *   1. read identity, settings, head, lease and the tab;
 *   2. price the tab and apply the tenders (rejecting anything that does not sum);
 *   3. decide fiscal numbering from the lease (NUMBERED or FISCAL_PENDING);
 *   4. build the body, chain it onto the head, sign the digest with the terminal key;
 *   5. commit entry + head + lease + tab-close in one transaction.
 * A failure anywhere before step 5 writes nothing.
 */
import { fromHex, toHex } from "./bytes";
import { type ChainEntry, chainDigest, GENESIS, instantFromMillis } from "./chain";
import { withLock } from "./db";
import { decideFiscal, type FiscalLease } from "./fiscal";
import { commitSale, getHead, getLease } from "./journal-store";
import { signDigest } from "./keys";
import type { JournalHead, JournalRecord, StoredLease, TabLine } from "./records";
import { applyTender, buildSaleBody, type PaymentInput, priceCart, type SaleCashier, saleBodyDigest } from "./sale";
import { getTab } from "./tab-store";
import { getIdentity, getSettings } from "./terminal-store";

export class NotEnrolledError extends Error {
  constructor() {
    super("This terminal is not enrolled, so it has no identity to sign sales with.");
    this.name = "NotEnrolledError";
  }
}

export class SaleRefusedError extends Error {}

const fromStored = (l: StoredLease): FiscalLease => ({
  terminalId: l.terminalId,
  firstNumber: BigInt(l.firstNumber),
  lastNumber: BigInt(l.lastNumber),
  nextNumber: BigInt(l.nextNumber),
  issuedAtMs: l.issuedAtMs,
  expiresAtMs: l.expiresAtMs
});

const toStored = (l: FiscalLease): StoredLease => ({
  terminalId: l.terminalId,
  firstNumber: l.firstNumber.toString(),
  lastNumber: l.lastNumber.toString(),
  nextNumber: l.nextNumber.toString(),
  issuedAtMs: l.issuedAtMs,
  expiresAtMs: l.expiresAtMs
});

export function tabLinesToCart(lines: TabLine[]) {
  return lines.map((l) => ({
    sku: l.sku,
    name: l.name,
    unitMinor: BigInt(l.unitMinor),
    taxBp: l.taxBp,
    qty: l.qty
  }));
}

export async function recordSale(input: {
  tabId: string;
  payments: PaymentInput[];
  nowMs?: number;
  /** Who is serving. Committed to by the chain when present (sale body v2). */
  cashier?: SaleCashier;
}): Promise<JournalRecord> {
  return withLock("mara-journal", async () => {
    const identity = await getIdentity();
    if (!identity) throw new NotEnrolledError();
    const settings = await getSettings();
    const tab = await getTab(input.tabId);
    if (!tab) throw new SaleRefusedError("That tab is already settled or was discarded.");
    if (tab.lines.length === 0) throw new SaleRefusedError("The tab is empty.");

    const head = await getHead();
    const storedLease = await getLease();

    const totals = priceCart(settings.currency, tabLinesToCart(tab.lines));
    const tender = applyTender(totals.total, input.payments);
    if (!tender.ok) throw new SaleRefusedError(tender.error);

    const requestedMs = input.nowMs ?? Date.now();
    const lastMs = head ? head.lastEpochSecond * 1000 + Math.floor(head.lastNano / 1_000_000) : 0;
    // The chain refuses a clock that runs backwards (NON_MONOTONIC_CLOCK). A till must
    // still be able to sell if its clock is set back, so the recorded time is clamped to
    // the previous entry's rather than the sale being refused.
    const nowMs = Math.max(requestedMs, lastMs);

    const decision = decideFiscal(storedLease ? fromStored(storedLease) : null, nowMs);
    const body = buildSaleBody(settings.currency, totals, tender.applied, {
      status: decision.status,
      number: decision.number
    }, input.cashier);

    const sequence = (head?.lastSequence ?? 0) + 1;
    const previousDigest = head ? fromHex(head.headDigest) : GENESIS;
    const { epochSecond, nano } = instantFromMillis(nowMs);
    const bodyDigest = await saleBodyDigest(body);
    const entry: ChainEntry = {
      terminalId: identity.terminalId,
      sequence: BigInt(sequence),
      epochSecond,
      nano,
      bodyDigest,
      previousDigest
    };
    const digest = await chainDigest(entry);
    const signature = await signDigest(identity.privateKey, digest);

    const record: JournalRecord = {
      sequence,
      terminalId: identity.terminalId,
      epochSecond: Number(epochSecond),
      nano,
      sale: body,
      bodyDigest: toHex(bodyDigest),
      previousDigest: toHex(previousDigest),
      digest: toHex(digest),
      signature
    };
    const newHead: JournalHead = {
      lastSequence: sequence,
      headDigest: record.digest,
      genesisDigest: head?.genesisDigest ?? record.digest,
      lastEpochSecond: record.epochSecond,
      lastNano: nano
    };

    await commitSale({
      record,
      head: newHead,
      expectedPreviousSequence: head?.lastSequence ?? 0,
      expectedLeaseNext: storedLease?.nextNumber ?? null,
      lease: decision.status === "NUMBERED" && decision.lease ? toStored(decision.lease) : null,
      tabId: tab.id
    });
    return record;
  });
}
