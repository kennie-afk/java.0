import { done, openDb, STORE, wrap } from "./db";
import { clampLimit, collect, type Page } from "./page";
import type { JournalHead, JournalRecord, StoredLease } from "./records";

export async function getHead(): Promise<JournalHead | null> {
  const db = await openDb();
  const v = await wrap(db.transaction(STORE.meta).objectStore(STORE.meta).get("journalHead"));
  return (v as JournalHead | undefined) ?? null;
}

export async function getLease(): Promise<StoredLease | null> {
  const db = await openDb();
  const v = await wrap(db.transaction(STORE.meta).objectStore(STORE.meta).get("fiscalLease"));
  return (v as StoredLease | undefined) ?? null;
}

export async function getEntry(sequence: number): Promise<JournalRecord | null> {
  const db = await openDb();
  const v = await wrap(db.transaction(STORE.journal).objectStore(STORE.journal).get(sequence));
  return (v as JournalRecord | undefined) ?? null;
}

/** Newest first, keyset-paged on the sequence number. `before` is exclusive. */
export async function listEntries(opts: {
  before?: number | null;
  limit?: number;
}): Promise<Page<JournalRecord, number>> {
  const limit = clampLimit(opts.limit);
  const db = await openDb();
  const store = db.transaction(STORE.journal).objectStore(STORE.journal);
  const range = opts.before ? IDBKeyRange.upperBound(opts.before, true) : undefined;
  const { rows, more } = await collect<JournalRecord>(store, range, "prev", limit);
  const last = rows[rows.length - 1];
  return { items: rows, next: more && last ? last.sequence : null };
}

/** Ascending page used by the verifier: entries with sequence > `after`. */
export async function readAscending(after: number, limit: number): Promise<JournalRecord[]> {
  const db = await openDb();
  const store = db.transaction(STORE.journal).objectStore(STORE.journal);
  const { rows } = await collect<JournalRecord>(store, IDBKeyRange.lowerBound(after, true), "next", limit);
  return rows;
}

export async function countEntries(): Promise<number> {
  const db = await openDb();
  return wrap(db.transaction(STORE.journal).objectStore(STORE.journal).count());
}

export class StaleJournalError extends Error {
  constructor() {
    super("the journal changed while this sale was being signed; nothing was written");
    this.name = "StaleJournalError";
  }
}

/**
 * Appends one entry, moves the head, updates the fiscal lease and closes the tab in ONE
 * transaction. The head and lease read at the start of the sale are re-checked inside
 * the transaction, so a second writer that got past the cross-tab lock is refused
 * rather than allowed to reuse a sequence number or a fiscal number.
 */
export async function commitSale(args: {
  record: JournalRecord;
  head: JournalHead;
  expectedPreviousSequence: number;
  expectedLeaseNext: string | null;
  lease: StoredLease | null;
  tabId: string | null;
}): Promise<void> {
  const db = await openDb();
  const tx = db.transaction([STORE.journal, STORE.meta, STORE.tabs], "readwrite");
  const finished = done(tx);
  finished.catch(() => undefined);
  const meta = tx.objectStore(STORE.meta);

  const currentHead = (await wrap(meta.get("journalHead"))) as JournalHead | undefined;
  const currentLease = (await wrap(meta.get("fiscalLease"))) as StoredLease | undefined;
  if ((currentHead?.lastSequence ?? 0) !== args.expectedPreviousSequence) {
    tx.abort();
    throw new StaleJournalError();
  }
  if ((currentLease?.nextNumber ?? null) !== args.expectedLeaseNext) {
    tx.abort();
    throw new StaleJournalError();
  }
  if (args.tabId) {
    const tab = await wrap(tx.objectStore(STORE.tabs).get(args.tabId));
    if (!tab) {
      tx.abort();
      throw new Error("that tab was already settled or discarded");
    }
    tx.objectStore(STORE.tabs).delete(args.tabId);
  }

  tx.objectStore(STORE.journal).add(args.record);   // add, not put: a reused sequence is refused
  meta.put(args.head, "journalHead");
  if (args.lease) meta.put(args.lease, "fiscalLease");
  await finished;
}
