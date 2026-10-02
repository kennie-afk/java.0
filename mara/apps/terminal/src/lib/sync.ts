/**
 * The terminal's side of the server conversation: upload the journal, keep a fiscal lease.
 *
 * Nothing here is on the selling path. A sale is recorded, signed and chained locally whether
 * or not any of this works; this module only ever *adds* a second copy of what the journal
 * already holds, and numbers for the next invoices. Every failure is therefore recorded and
 * retried, never thrown into a sale.
 *
 * Every request is signed with the terminal's enrolment key over
 * `mara.request.v1|terminal|epoch|METHOD|path|sha256(body)` (the server's RequestSignature),
 * so the server believes it as that terminal and nothing in transit can be altered or
 * replayed against another path.
 */
import { toHex, utf8 } from "./bytes";
import { sha256 } from "./chain";
import { withLock } from "./db";
import { needsRenewal, type FiscalLease } from "./fiscal";
import { getHead, getLease, readAscending } from "./journal-store";
import { signDigest } from "./keys";
import { getMeta, putMeta } from "./meta";
import type { PendingReturn, StoredLease, SyncState, TerminalIdentity } from "./records";
import { getIdentity } from "./terminal-store";

export const UPLOAD_BATCH = 100;
const SYNC_KEY = "syncState";
const RETURNS_KEY = "pendingReturns";

export const EMPTY_SYNC: SyncState = {
  syncedThrough: 0,
  lastAttemptMs: null,
  lastSuccessMs: null,
  lastError: null,
  heldAtGap: false,
  openExceptions: 0
};

export const getSyncState = async (): Promise<SyncState> => ({ ...EMPTY_SYNC, ...((await getMeta<SyncState>(SYNC_KEY)) ?? {}) });
const saveSyncState = (s: SyncState) => putMeta(SYNC_KEY, s);

export function requestMessage(terminalId: string, epochSecond: number, method: string, path: string, bodyHashHex: string): Uint8Array {
  return utf8(`mara.request.v1|${terminalId}|${epochSecond}|${method.toUpperCase()}|${path}|${bodyHashHex}`);
}

/** Signs one request. `body` is the exact string that will be sent. */
export async function signedHeaders(
  identity: TerminalIdentity,
  method: string,
  path: string,
  body: string,
  nowMs = Date.now()
): Promise<Record<string, string>> {
  const epochSecond = Math.floor(nowMs / 1000);
  const hash = toHex(await sha256(utf8(body)));
  const signature = await signDigest(identity.privateKey, requestMessage(identity.terminalId, epochSecond, method, path, hash));
  return {
    "content-type": "application/json",
    "x-mara-terminal": identity.terminalId,
    "x-mara-timestamp": String(epochSecond),
    "x-mara-signature": signature
  };
}

type Fetch = typeof fetch;

async function call(
  identity: TerminalIdentity,
  route: string,
  upstreamPath: string,
  method: "GET" | "POST",
  payload: unknown,
  fetchImpl: Fetch
): Promise<{ status: number; json: Record<string, unknown> | null }> {
  const body = method === "POST" ? JSON.stringify(payload ?? {}) : "";
  const headers = await signedHeaders(identity, method, upstreamPath, body);
  const response = await fetchImpl(route, { method, headers, body: method === "POST" ? body : undefined, cache: "no-store" });
  const json = (await response.json().catch(() => null)) as Record<string, unknown> | null;
  return { status: response.status, json };
}

export interface SyncOptions {
  fetchImpl?: Fetch;
  nowMs?: number;
}

/**
 * Uploads everything after the confirmed cursor, in batches, until the server is caught up or
 * says stop. The cursor only ever moves to what the server reports it now holds.
 */
export async function syncJournal(options: SyncOptions = {}): Promise<SyncState> {
  const fetchImpl = options.fetchImpl ?? fetch;
  const identity = await getIdentity();
  let state = await getSyncState();
  if (!identity) return state;
  const now = options.nowMs ?? Date.now();
  state = { ...state, lastAttemptMs: now };
  try {
    for (;;) {
      const head = await getHead();
      if (!head || state.syncedThrough >= head.lastSequence) {
        state = { ...state, lastError: null, lastSuccessMs: now };
        break;
      }
      const entries = await readAscending(state.syncedThrough, UPLOAD_BATCH);
      if (entries.length === 0) break;
      const { status, json } = await call(identity, "/api/sync/journal", "/v1/terminal/sync/journal", "POST", { entries }, fetchImpl);
      if (status !== 200 || !json) {
        state = { ...state, lastError: describe(status, json) };
        break;
      }
      const through = Number(json.acceptedThrough);
      const gap = json.gap as { from: number; to: number } | null;
      const refused = (json.refused as unknown[] | undefined)?.length ?? 0;
      // Never beyond what was actually sent, whatever the server says.
      const confirmed = Math.max(state.syncedThrough, Math.min(through, entries[entries.length - 1].sequence));
      state = { ...state, syncedThrough: confirmed, heldAtGap: !!gap, lastSuccessMs: now, lastError: null };
      if (gap) {
        state = { ...state, lastError: `The server is missing sequence ${gap.from}${gap.to > gap.from ? `-${gap.to}` : ""} from this terminal's upload.` };
        break;
      }
      if (refused > 0) {
        state = { ...state, lastError: `${refused} entr${refused === 1 ? "y was" : "ies were"} refused by the server's verification.` };
        break;
      }
      if (confirmed < entries[entries.length - 1].sequence) break;
    }
    // A cheap read of how the server sees us, for the exceptions count.
    const st = await call(identity, "/api/sync/status", "/v1/terminal/sync/status", "GET", null, fetchImpl);
    if (st.status === 200 && st.json) {
      state = { ...state, openExceptions: Number(st.json.openExceptions ?? 0), heldAtGap: state.heldAtGap || st.json.heldAtGap === true };
    }
  } catch {
    state = { ...state, lastError: "The server could not be reached; sales are safe on this device and will upload later." };
  }
  await saveSyncState(state);
  return state;
}

function describe(status: number, json: Record<string, unknown> | null): string {
  if (status === 401) return "The server did not accept this terminal's signature (is it still enrolled and active?).";
  if (status === 502 || status === 503) return "The sync service is not reachable.";
  return `The server answered ${status}${json && typeof json.error === "string" ? `: ${json.error}` : ""}.`;
}

// ------------------------------------------------------------------ fiscal

const fromStored = (l: StoredLease): FiscalLease => ({
  terminalId: l.terminalId,
  firstNumber: BigInt(l.firstNumber),
  lastNumber: BigInt(l.lastNumber),
  nextNumber: BigInt(l.nextNumber),
  issuedAtMs: l.issuedAtMs,
  expiresAtMs: l.expiresAtMs
});

export async function getPendingReturns(): Promise<PendingReturn[]> {
  return (await getMeta<PendingReturn[]>(RETURNS_KEY)) ?? [];
}

/**
 * Keeps a fiscal lease on hand. Renews at 20% remaining or on expiry or exhaustion. The new
 * lease is installed first, in one step under the journal lock so no sale can interleave, and
 * the old lease's unused tail is queued to be handed back afterwards: a sale never waits on
 * the network, and the numbers are voided (never recycled) when the return lands.
 */
export async function maintainLease(options: SyncOptions = {}): Promise<{ installed: boolean; error: string | null }> {
  const fetchImpl = options.fetchImpl ?? fetch;
  const identity = await getIdentity();
  if (!identity) return { installed: false, error: "not enrolled" };
  const nowMs = options.nowMs ?? Date.now();
  const current = await getLease();
  if (current && !needsRenewal(fromStored(current), nowMs)) {
    await flushReturns(identity, fetchImpl);
    return { installed: false, error: null };
  }

  let issued: Record<string, unknown> | null = null;
  try {
    const r = await call(identity, "/api/fiscal/lease", "/v1/terminal/fiscal/leases", "POST", {}, fetchImpl);
    if (r.status === 409) {
      // Two live leases already: hand back the oldest unused tail first, then try again next cycle.
      await flushReturns(identity, fetchImpl);
      return { installed: false, error: "This terminal already holds the maximum number of live fiscal leases." };
    }
    if (r.status !== 201 || !r.json) return { installed: false, error: describe(r.status, r.json) };
    issued = r.json;
  } catch {
    return { installed: false, error: "The fiscal service could not be reached." };
  }

  const fresh: StoredLease = {
    leaseId: String(issued.leaseId),
    terminalId: identity.terminalId,
    firstNumber: String(issued.firstNumber),
    lastNumber: String(issued.lastNumber),
    nextNumber: String(issued.nextNumber),
    issuedAtMs: Number(issued.issuedAtMs),
    expiresAtMs: Number(issued.expiresAtMs)
  };
  const installed = await withLock("mara-journal", async () => {
    const latest = await getLease();
    if (latest && !needsRenewal(fromStored(latest), nowMs)) {
      // Another tab renewed while we waited: give this whole lease straight back.
      await queueReturn({ leaseId: fresh.leaseId!, nextUnused: fresh.firstNumber });
      return false;
    }
    if (latest?.leaseId && BigInt(latest.nextNumber) <= BigInt(latest.lastNumber)) {
      await queueReturn({ leaseId: latest.leaseId, nextUnused: latest.nextNumber });
    }
    await putMeta("fiscalLease", fresh);
    return true;
  });
  await flushReturns(identity, fetchImpl);
  return { installed, error: null };
}

async function queueReturn(r: PendingReturn): Promise<void> {
  const list = await getPendingReturns();
  if (!list.some((x) => x.leaseId === r.leaseId)) await putMeta(RETURNS_KEY, [...list, r]);
}

async function flushReturns(identity: TerminalIdentity, fetchImpl: Fetch): Promise<void> {
  const list = await getPendingReturns();
  if (list.length === 0) return;
  const remaining: PendingReturn[] = [];
  for (const r of list) {
    try {
      const res = await call(identity, `/api/fiscal/return/${r.leaseId}`, `/v1/terminal/fiscal/leases/${r.leaseId}/return`, "POST", { nextUnused: r.nextUnused }, fetchImpl);
      // 200 done; 404 not ours or unknown: neither will ever succeed on retry.
      if (res.status !== 200 && res.status !== 404) remaining.push(r);
    } catch {
      remaining.push(r);
    }
  }
  await putMeta(RETURNS_KEY, remaining);
}

/** One full cycle: renew the lease if needed, then upload. Serialised across tabs. */
export async function syncCycle(options: SyncOptions = {}): Promise<SyncState | null> {
  if (typeof navigator !== "undefined" && navigator.onLine === false) return null;
  return withLock("mara-sync", async () => {
    await maintainLease(options);
    return syncJournal(options);
  });
}


