import { done, openDb, STORE, wrap } from "./db";
import { clampLimit, collect, type Page } from "./page";
import type { CatalogueItem, Tab, TabLine } from "./records";

export type TabCursor = [openedAt: number, id: string];

export async function openTab(label: string): Promise<Tab> {
  const tab: Tab = { id: crypto.randomUUID(), label: label.trim(), openedAt: Date.now(), lines: [] };
  const db = await openDb();
  const tx = db.transaction(STORE.tabs, "readwrite");
  tx.objectStore(STORE.tabs).add(tab);
  await done(tx);
  return tab;
}

export async function getTab(id: string): Promise<Tab | null> {
  const db = await openDb();
  const value = await wrap(db.transaction(STORE.tabs).objectStore(STORE.tabs).get(id));
  return (value as Tab | undefined) ?? null;
}

async function mutate(id: string, change: (tab: Tab) => void): Promise<Tab> {
  const db = await openDb();
  const tx = db.transaction(STORE.tabs, "readwrite");
  const store = tx.objectStore(STORE.tabs);
  const tab = (await wrap(store.get(id))) as Tab | undefined;
  if (!tab) {
    tx.abort();
    throw new Error("that tab is closed or no longer exists");
  }
  change(tab);
  store.put(tab);
  await done(tx);
  return tab;
}

/** Adds one of an item, or bumps the quantity if the item is already on the tab. */
export function addToTab(id: string, item: CatalogueItem): Promise<Tab> {
  return mutate(id, (tab) => {
    const existing = tab.lines.find((l) => l.itemId === item.id);
    if (existing) {
      existing.qty += 1;
      return;
    }
    // The line snapshots name, price and rate: editing the catalogue later must not
    // reprice a tab that is already open.
    const line: TabLine = {
      itemId: item.id,
      sku: item.sku,
      name: item.name,
      unitMinor: item.unitMinor,
      taxBp: item.taxBp,
      qty: 1
    };
    tab.lines.push(line);
  });
}

export function setQuantity(id: string, itemId: string, qty: number): Promise<Tab> {
  return mutate(id, (tab) => {
    if (!Number.isInteger(qty) || qty < 0 || qty > 100_000) throw new RangeError("quantity out of range");
    tab.lines = qty === 0 ? tab.lines.filter((l) => l.itemId !== itemId) : tab.lines.map((l) => (l.itemId === itemId ? { ...l, qty } : l));
  });
}

export async function discardTab(id: string): Promise<void> {
  const db = await openDb();
  const tx = db.transaction(STORE.tabs, "readwrite");
  tx.objectStore(STORE.tabs).delete(id);
  await done(tx);
}

/** Open tabs, most recent first, keyset-paged. */
export async function listTabs(opts: { after?: TabCursor | null; limit?: number }): Promise<Page<Tab, TabCursor>> {
  const limit = clampLimit(opts.limit, 20);
  const db = await openDb();
  const index = db.transaction(STORE.tabs).objectStore(STORE.tabs).index("byOpened");
  const range = opts.after ? IDBKeyRange.upperBound(opts.after, true) : undefined;
  const { rows, more } = await collect<Tab>(index, range, "prev", limit);
  const last = rows[rows.length - 1];
  return { items: rows, next: more && last ? [last.openedAt, last.id] : null };
}
