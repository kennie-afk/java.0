import { done, openDb, STORE, wrap } from "./db";
import { clampLimit, collect, type Page } from "./page";
import type { CatalogueItem } from "./records";

export type CatalogueCursor = [nameKey: string, id: string];

export interface CatalogueInput {
  sku: string;
  name: string;
  unitMinor: bigint;
  taxBp: number;
}

export class DuplicateSkuError extends Error {
  constructor(sku: string) {
    super(`An item with SKU "${sku}" already exists.`);
    this.name = "DuplicateSkuError";
  }
}

const skuKeyOf = (sku: string) => sku.trim().toLowerCase();
const nameKeyOf = (name: string) => name.trim().toLowerCase();

function toRecord(input: CatalogueInput, id: string, createdAt: number): CatalogueItem {
  return {
    id,
    sku: input.sku.trim(),
    skuKey: skuKeyOf(input.sku),
    name: input.name.trim(),
    nameKey: nameKeyOf(input.name),
    unitMinor: input.unitMinor.toString(),
    taxBp: input.taxBp,
    createdAt
  };
}

async function put(record: CatalogueItem, mode: "add" | "put"): Promise<void> {
  const db = await openDb();
  const tx = db.transaction(STORE.catalogue, "readwrite");
  const finished = done(tx);
  finished.catch(() => undefined);
  const request = tx.objectStore(STORE.catalogue)[mode](record);
  try {
    await wrap(request);
    await finished;
  } catch (e) {
    if ((request.error ?? (e as DOMException))?.name === "ConstraintError") {
      throw new DuplicateSkuError(record.sku);
    }
    throw e;
  }
}

export async function createItem(input: CatalogueInput): Promise<CatalogueItem> {
  const record = toRecord(input, crypto.randomUUID(), Date.now());
  await put(record, "add");
  return record;
}

export async function updateItem(id: string, input: CatalogueInput): Promise<CatalogueItem> {
  const existing = await getItem(id);
  if (!existing) throw new Error("that item no longer exists");
  const record = toRecord(input, id, existing.createdAt);
  await put(record, "put");
  return record;
}

export async function getItem(id: string): Promise<CatalogueItem | null> {
  const db = await openDb();
  const value = await wrap(db.transaction(STORE.catalogue).objectStore(STORE.catalogue).get(id));
  return (value as CatalogueItem | undefined) ?? null;
}

export async function deleteItem(id: string): Promise<void> {
  const db = await openDb();
  const tx = db.transaction(STORE.catalogue, "readwrite");
  tx.objectStore(STORE.catalogue).delete(id);
  await done(tx);
}

export async function countItems(): Promise<number> {
  const db = await openDb();
  return wrap(db.transaction(STORE.catalogue).objectStore(STORE.catalogue).count());
}

/**
 * One page of the catalogue ordered by name. `q` is a case-insensitive name prefix; an
 * exact SKU match (a barcode scan) is prepended on the first page. Paging is keyset, on
 * the (name, id) index, so page N costs the same as page 1 however large the catalogue.
 */
export async function listItems(opts: {
  q?: string;
  after?: CatalogueCursor | null;
  limit?: number;
}): Promise<Page<CatalogueItem, CatalogueCursor>> {
  const limit = clampLimit(opts.limit);
  const db = await openDb();
  const tx = db.transaction(STORE.catalogue);
  const index = tx.objectStore(STORE.catalogue).index("byName");
  const q = nameKeyOf(opts.q ?? "");

  const low: CatalogueCursor = opts.after ?? [q, ""];
  const lowerOpen = Boolean(opts.after);
  const range = q
    ? IDBKeyRange.bound(low, [q + "￿", "￿"], lowerOpen, false)
    : opts.after
      ? IDBKeyRange.lowerBound(opts.after, true)
      : undefined;

  const { rows, more } = await collect<CatalogueItem>(index, range, "next", limit);
  let items = rows;

  if (q && !opts.after) {
    const bySku = await wrap(tx.objectStore(STORE.catalogue).index("bySku").get(q));
    const hit = bySku as CatalogueItem | undefined;
    if (hit && !items.some((i) => i.id === hit.id)) items = [hit, ...items].slice(0, limit);
  }

  const last = rows[rows.length - 1];
  return { items, next: more && last ? [last.nameKey, last.id] : null };
}
