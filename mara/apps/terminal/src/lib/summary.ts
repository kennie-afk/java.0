/**
 * End-of-day summary computed from the terminal's own journal: what a Z-report is, and
 * nothing more than a reading of signed entries, so it cannot disagree with the journal.
 */
import { readAscending } from "./journal-store";
import type { JournalRecord } from "./records";

export interface DaySummary {
  day: string; // local YYYY-MM-DD
  sales: number;
  grossMinor: bigint;
  netMinor: bigint;
  taxMinor: bigint;
  cashMinor: bigint;
  mobileMinor: bigint;
  pending: number;
}

export interface ItemSummary {
  name: string;
  qty: number;
  netMinor: bigint;
}

export interface CashierSummary {
  name: string;
  sales: number;
  grossMinor: bigint;
}

export interface Summary {
  days: DaySummary[];
  items: ItemSummary[];
  cashiers: CashierSummary[];
  entries: number;
}

export function localDay(record: JournalRecord): string {
  const d = new Date(record.epochSecond * 1000 + Math.floor(record.nano / 1e6));
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

export function summarise(records: JournalRecord[]): Summary {
  const days = new Map<string, DaySummary>();
  const items = new Map<string, ItemSummary>();
  const cashiers = new Map<string, CashierSummary>();
  for (const r of records) {
    const key = localDay(r);
    const d =
      days.get(key) ??
      ({ day: key, sales: 0, grossMinor: 0n, netMinor: 0n, taxMinor: 0n, cashMinor: 0n, mobileMinor: 0n, pending: 0 } as DaySummary);
    d.sales += 1;
    d.grossMinor += BigInt(r.sale.totalMinor);
    for (const l of r.sale.lines) {
      d.netMinor += BigInt(l.netMinor);
      d.taxMinor += BigInt(l.taxMinor);
      const it = items.get(l.sku) ?? { name: l.name, qty: 0, netMinor: 0n };
      it.qty += l.qty;
      it.netMinor += BigInt(l.netMinor);
      items.set(l.sku, it);
    }
    for (const p of r.sale.payments) {
      if (p.method === "CASH") d.cashMinor += BigInt(p.appliedMinor);
      else d.mobileMinor += BigInt(p.appliedMinor);
    }
    if (r.sale.fiscal.status === "FISCAL_PENDING") d.pending += 1;
    days.set(key, d);
    const who = r.sale.cashier?.name ?? "(not signed in)";
    const c = cashiers.get(who) ?? { name: who, sales: 0, grossMinor: 0n };
    c.sales += 1;
    c.grossMinor += BigInt(r.sale.totalMinor);
    cashiers.set(who, c);
  }
  const byNet = (a: ItemSummary, b: ItemSummary) => (b.netMinor > a.netMinor ? 1 : b.netMinor < a.netMinor ? -1 : 0);
  return {
    days: [...days.values()].sort((a, b) => (a.day < b.day ? 1 : -1)),
    items: [...items.values()].sort(byNet).slice(0, 10),
    cashiers: [...cashiers.values()].sort((a, b) => (b.grossMinor > a.grossMinor ? 1 : -1)),
    entries: records.length
  };
}

/** Reads the whole journal in ascending pages (bounded memory per page) and summarises it. */
export async function summariseJournal(pageSize = 200): Promise<Summary> {
  const all: JournalRecord[] = [];
  let after = 0;
  for (;;) {
    const rows = await readAscending(after, pageSize);
    if (rows.length === 0) break;
    all.push(...rows);
    after = rows[rows.length - 1].sequence;
    if (rows.length < pageSize) break;
  }
  return summarise(all);
}
