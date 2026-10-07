"use client";

import { useCallback, useEffect, useState } from "react";
import { ApiError } from "@/lib/api";

/**
 * Writes made while the connection is down. Only bedside data capture (vitals, notes, visits, doses) can be queued: a
 * write that needs a live decision from the server (an order checked against allergies, a payment, a result) never is.
 * Each queued write carries its own Idempotency-Key, so sending it twice, because the connection dropped after the
 * server had already acted, records it once. A write the server refuses is kept as FAILED and shown, never dropped.
 *
 * The queue lives in this browser's IndexedDB, which is data about patients at rest on this device.
 */
export type Queued = { id: string; method: "POST"; path: string; body: unknown; label: string; createdAt: number; status: "PENDING" | "FAILED"; attempts: number; error?: string };

const QUEUEABLE = [
  /^\/v1\/clinical\/encounters\/[0-9a-f-]{36}\/(vitals|notes)$/,
  /^\/v1\/programmes\/enrolments\/[0-9a-f-]{36}\/visits$/,
  /^\/v1\/mch\/pregnancies\/[0-9a-f-]{36}\/visits$/,
  /^\/v1\/mch\/immunisation\/patients\/[0-9a-f-]{36}\/doses$/
];

export const queueable = (path: string) => QUEUEABLE.some((r) => r.test(path));

const DB = "hms-outbox";
const STORE = "writes";
const EVENT = "hms-outbox-changed";

function open(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    if (typeof indexedDB === "undefined") return reject(new Error("This browser cannot keep writes for later."));
    const req = indexedDB.open(DB, 1);
    req.onupgradeneeded = () => req.result.createObjectStore(STORE, { keyPath: "id" });
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error ?? new Error("Could not open local storage."));
  });
}

async function tx<T>(mode: IDBTransactionMode, fn: (s: IDBObjectStore) => IDBRequest<T>): Promise<T> {
  const db = await open();
  return new Promise<T>((resolve, reject) => {
    const t = db.transaction(STORE, mode);
    const r = fn(t.objectStore(STORE));
    t.oncomplete = () => { db.close(); resolve(r.result); };
    t.onerror = () => { db.close(); reject(t.error); };
    t.onabort = () => { db.close(); reject(t.error); };
  });
}

const changed = () => window.dispatchEvent(new Event(EVENT));

export async function listQueued(): Promise<Queued[]> {
  try {
    const all = await tx<Queued[]>("readonly", (s) => s.getAll());
    return all.sort((a, b) => a.createdAt - b.createdAt);
  } catch {
    return [];
  }
}

async function put(item: Queued) {
  await tx("readwrite", (s) => s.put(item));
  changed();
}

export async function discard(id: string) {
  await tx("readwrite", (s) => s.delete(id));
  changed();
}

export async function retry(id: string) {
  const all = await listQueued();
  const q = all.find((x) => x.id === id);
  if (q) await put({ ...q, status: "PENDING", error: undefined, attempts: 0 });
}

const newKey = () => `hms-${crypto.randomUUID()}`;

async function send(q: Pick<Queued, "id" | "path" | "body">): Promise<Response> {
  return fetch(`/api${q.path}`, { method: "POST", headers: { "Content-Type": "application/json", "Idempotency-Key": q.id }, body: JSON.stringify(q.body) });
}

export type QueuedResult<T> = { queued: false; data: T } | { queued: true; id: string };

/**
 * Sends a write now, or keeps it for later if there is no connection. A refusal from the server (validation, a closed
 * encounter, no permission) is thrown at once so the person can fix it; only a missing connection queues.
 */
export async function postQueued<T = unknown>(path: string, body: unknown, label: string): Promise<QueuedResult<T>> {
  if (!queueable(path)) throw new Error("That write cannot be kept for later.");
  const item: Queued = { id: newKey(), method: "POST", path, body, label, createdAt: Date.now(), status: "PENDING", attempts: 0 };
  const keep = async (): Promise<QueuedResult<T>> => {
    await put(item);
    return { queued: true, id: item.id };
  };
  if (typeof navigator !== "undefined" && navigator.onLine === false) return keep();
  let res: Response;
  try {
    res = await send(item);
  } catch {
    // The request may or may not have reached the server; the key makes resending it safe.
    return keep();
  }
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (res.status === 401) {
    // The session could not be renewed (it was ended, or the person was signed out elsewhere). The entry is not lost: it waits on this
    // device and is sent, in order, after the next sign-in.
    const kept = await keep();
    if (typeof window !== "undefined") window.location.href = "/login";
    return kept;
  }
  if (!res.ok) throw new ApiError(res.status, data ?? {});
  return { queued: false, data: data as T };
}

let flushing = false;

/** Sends waiting writes oldest first. Stops at the first sign the connection is down, so order is kept. */
export async function flush(): Promise<void> {
  if (flushing || (typeof navigator !== "undefined" && navigator.onLine === false)) return;
  flushing = true;
  try {
    for (const q of (await listQueued()).filter((x) => x.status === "PENDING")) {
      let res: Response;
      try {
        res = await send(q);
      } catch {
        return;
      }
      if (res.ok) {
        await discard(q.id);
        continue;
      }
      const body = await res.json().catch(() => ({}));
      if (res.status === 401) return;
      if (res.status >= 500 || res.status === 429 || (res.status === 409 && body.code === "request_in_progress")) {
        await put({ ...q, attempts: q.attempts + 1, error: "The server could not take it yet." });
        return;
      }
      await put({ ...q, status: "FAILED", attempts: q.attempts + 1, error: typeof body.detail === "string" ? body.detail : `The server refused it (${res.status}).` });
    }
  } finally {
    flushing = false;
  }
}

export function useOutbox() {
  const [items, setItems] = useState<Queued[]>([]);
  const [online, setOnline] = useState(true);
  const refresh = useCallback(async () => setItems(await listQueued()), []);
  useEffect(() => {
    setOnline(navigator.onLine);
    void refresh();
    const onChange = () => void refresh();
    const goOnline = () => { setOnline(true); void flush().then(refresh); };
    const goOffline = () => setOnline(false);
    window.addEventListener(EVENT, onChange);
    window.addEventListener("online", goOnline);
    window.addEventListener("offline", goOffline);
    const timer = window.setInterval(() => void flush().then(refresh), 30_000);
    void flush().then(refresh);
    return () => {
      window.removeEventListener(EVENT, onChange);
      window.removeEventListener("online", goOnline);
      window.removeEventListener("offline", goOffline);
      window.clearInterval(timer);
    };
  }, [refresh]);
  return { items, online, pending: items.filter((i) => i.status === "PENDING").length, failed: items.filter((i) => i.status === "FAILED").length, refresh };
}

/** Forgets the saved copies of pages and records the service worker keeps for offline reading. Called on sign-out. */
export async function clearOfflineCaches(): Promise<void> {
  try {
    if (typeof caches !== "undefined") {
      for (const name of await caches.keys()) {
        if (name.startsWith("hms-api-") || name.startsWith("hms-pages-")) await caches.delete(name);
      }
    }
  } catch {
    // nothing to clear, or the browser refused
  }
}
