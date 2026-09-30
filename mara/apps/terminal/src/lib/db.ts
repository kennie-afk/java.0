/**
 * The terminal's local database. Everything the till knows lives here, in this browser's
 * IndexedDB, and nothing has left the device.
 */
export const DB_NAME = "mara-terminal";
const DB_VERSION = 1;

export const STORE = {
  identity: "identity",
  settings: "settings",
  catalogue: "catalogue",
  tabs: "tabs",
  journal: "journal",
  meta: "meta"
} as const;

let opened: Promise<IDBDatabase> | null = null;

export function openDb(): Promise<IDBDatabase> {
  if (opened) return opened;
  opened = new Promise<IDBDatabase>((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);
    request.onupgradeneeded = () => {
      const db = request.result;
      db.createObjectStore(STORE.identity);
      db.createObjectStore(STORE.settings);
      db.createObjectStore(STORE.meta);
      const catalogue = db.createObjectStore(STORE.catalogue, { keyPath: "id" });
      catalogue.createIndex("byName", ["nameKey", "id"]);
      catalogue.createIndex("bySku", "skuKey", { unique: true });
      const tabs = db.createObjectStore(STORE.tabs, { keyPath: "id" });
      tabs.createIndex("byOpened", ["openedAt", "id"]);
      db.createObjectStore(STORE.journal, { keyPath: "sequence" });
    };
    request.onsuccess = () => {
      const db = request.result;
      // Another tab upgrading the schema must not be blocked by this connection.
      db.onversionchange = () => {
        db.close();
        opened = null;
      };
      resolve(db);
    };
    request.onerror = () => {
      opened = null;
      reject(request.error ?? new Error("could not open the local database"));
    };
    request.onblocked = () => reject(new Error("the local database is blocked by another tab"));
  });
  return opened;
}

/** Test hook: forget the cached connection so a fresh IDBFactory can be used. */
export function resetDbForTests(): void {
  opened = null;
}

export function wrap<T>(request: IDBRequest<T>): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

export function done(tx: IDBTransaction): Promise<void> {
  return new Promise<void>((resolve, reject) => {
    tx.oncomplete = () => resolve();
    tx.onerror = () => reject(tx.error);
    tx.onabort = () => reject(tx.error ?? new Error("transaction aborted"));
  });
}

/**
 * Runs `fn` while holding an exclusive cross-tab lock. Two tabs on one till would
 * otherwise both read the same journal head and race for the same sequence number.
 * Where Web Locks is unavailable the in-transaction head check in the journal writer
 * still refuses the second writer; the lock just spares it a retry.
 */
export async function withLock<T>(name: string, fn: () => Promise<T>): Promise<T> {
  const locks = (globalThis.navigator as Navigator | undefined)?.locks;
  if (locks?.request) {
    return locks.request(name, fn) as Promise<T>;
  }
  return fn();
}
