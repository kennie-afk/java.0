import { done, openDb, STORE, wrap } from "./db";

/** Small keyed values (sync cursor, queued lease returns) in the local `meta` store. */
export async function getMeta<T>(key: string): Promise<T | null> {
  const db = await openDb();
  const v = await wrap(db.transaction(STORE.meta).objectStore(STORE.meta).get(key));
  return (v as T | undefined) ?? null;
}

export async function putMeta(key: string, value: unknown): Promise<void> {
  const db = await openDb();
  const tx = db.transaction(STORE.meta, "readwrite");
  tx.objectStore(STORE.meta).put(value, key);
  await done(tx);
}
