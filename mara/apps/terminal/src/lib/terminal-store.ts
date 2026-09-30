import { done, openDb, STORE, wrap } from "./db";
import type { Settings, TerminalIdentity } from "./records";

const KEY = "terminal";

export const DEFAULT_SETTINGS: Settings = { currency: "KES", shopName: "" };

export async function getIdentity(): Promise<TerminalIdentity | null> {
  const db = await openDb();
  const value = await wrap(db.transaction(STORE.identity).objectStore(STORE.identity).get(KEY));
  return (value as TerminalIdentity | undefined) ?? null;
}

/**
 * Writes the identity only if none exists. Enrolment is once per device; a second
 * successful enrolment must never silently replace the key that signed the journal.
 */
export async function saveIdentityOnce(identity: TerminalIdentity): Promise<void> {
  const db = await openDb();
  const tx = db.transaction(STORE.identity, "readwrite");
  const store = tx.objectStore(STORE.identity);
  const existing = await wrap(store.get(KEY));
  if (existing) {
    tx.abort();
    throw new Error("this browser is already enrolled");
  }
  store.put(identity, KEY);
  await done(tx);
}

export async function getSettings(): Promise<Settings> {
  const db = await openDb();
  const value = await wrap(db.transaction(STORE.settings).objectStore(STORE.settings).get(KEY));
  return { ...DEFAULT_SETTINGS, ...((value as Partial<Settings> | undefined) ?? {}) };
}

export async function saveSettings(settings: Settings): Promise<void> {
  const db = await openDb();
  const tx = db.transaction(STORE.settings, "readwrite");
  tx.objectStore(STORE.settings).put(settings, KEY);
  await done(tx);
}
