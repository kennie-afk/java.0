/**
 * Staff sign-in at the till.
 *
 * Two paths, because a till must keep selling when the network is gone:
 *   - ONLINE: the terminal signs the attempt with its enrolment key and identity-service
 *     decides (Argon2id, branch rules, and the lockout that doubles per failure). Only a
 *     successful server sign-in teaches this device anything.
 *   - DEVICE: after one successful server sign-in, the device keeps a salted PBKDF2
 *     verifier of that member of staff's PIN (never the PIN) so they can sign in again with
 *     no network. The device applies the same doubling lockout locally.
 *
 * What the device path cannot do, and the UI says so: learn that a member of staff was
 * suspended, or share its failure count with the server, until a sync service exists.
 */
import { fromHex, toHex } from "./bytes";
import { done, openDb, STORE, wrap } from "./db";
import { signDigest } from "./keys";
import { getIdentity } from "./terminal-store";

export type StaffRole = "CASHIER" | "SUPERVISOR" | "MANAGER" | "OWNER";

export interface StaffSession {
  staffId: string;
  staffNumber: string;
  displayName: string;
  role: StaffRole;
  branchId: string | null;
  signedInAt: number;
  expiresAt: number;
  via: "server" | "device";
}

interface CachedStaff {
  staffId: string;
  staffNumber: string;
  displayName: string;
  role: StaffRole;
  branchId: string | null;
  saltHex: string;
  verifierHex: string;
  iterations: number;
  failedAttempts: number;
  lockedUntil: number | null;
}

export const SESSION_TTL_MS = 12 * 60 * 60 * 1000;
export const PBKDF2_ITERATIONS = 150_000;
const ATTEMPTS_BEFORE_LOCKOUT = 5;
const BASE_LOCKOUT_MS = 60_000;
const MAX_LOCKOUT_MS = 30 * 60_000;

/** Mirrors the platform PinPolicy: free until five failures, then 1, 2, 4, 8... minutes, capped at 30. */
export function lockoutAfterFailure(failures: number): number {
  if (failures < ATTEMPTS_BEFORE_LOCKOUT) return 0;
  const doublings = failures - ATTEMPTS_BEFORE_LOCKOUT;
  if (doublings >= 30) return MAX_LOCKOUT_MS;
  return Math.min(BASE_LOCKOUT_MS * 2 ** doublings, MAX_LOCKOUT_MS);
}

export type SignInResult =
  | { kind: "signed-in"; session: StaffSession }
  | { kind: "refused" }
  | { kind: "locked"; until: number; source: "server" | "device" }
  | { kind: "no-device-record" }
  | { kind: "not-enrolled" };

async function metaGet<T>(key: string): Promise<T | undefined> {
  const db = await openDb();
  return (await wrap(db.transaction(STORE.meta).objectStore(STORE.meta).get(key))) as T | undefined;
}

async function metaPut(key: string, value: unknown): Promise<void> {
  const db = await openDb();
  const tx = db.transaction(STORE.meta, "readwrite");
  tx.objectStore(STORE.meta).put(value, key);
  await done(tx);
}

async function metaDelete(key: string): Promise<void> {
  const db = await openDb();
  const tx = db.transaction(STORE.meta, "readwrite");
  tx.objectStore(STORE.meta).delete(key);
  await done(tx);
}

export async function getSession(nowMs = Date.now()): Promise<StaffSession | null> {
  const s = await metaGet<StaffSession>("session");
  if (!s) return null;
  if (s.expiresAt <= nowMs) {
    await metaDelete("session");
    return null;
  }
  return s;
}

export async function signOut(): Promise<void> {
  await metaDelete("session");
}

async function derive(pin: string, salt: Uint8Array, iterations: number): Promise<string> {
  const base = await crypto.subtle.importKey("raw", new TextEncoder().encode(pin), "PBKDF2", false, ["deriveBits"]);
  const bits = await crypto.subtle.deriveBits(
    { name: "PBKDF2", hash: "SHA-256", salt: new Uint8Array(salt), iterations },
    base,
    256
  );
  return toHex(new Uint8Array(bits));
}

function constantTimeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

const cacheKey = (staffNumber: string) => `staff:${staffNumber.trim().toLowerCase()}`;

async function remember(
  who: Pick<StaffSession, "staffId" | "staffNumber" | "displayName" | "role" | "branchId">,
  pin: string
): Promise<void> {
  const salt = crypto.getRandomValues(new Uint8Array(16));
  const record: CachedStaff = {
    ...who,
    saltHex: toHex(salt),
    verifierHex: await derive(pin, salt, PBKDF2_ITERATIONS),
    iterations: PBKDF2_ITERATIONS,
    failedAttempts: 0,
    lockedUntil: null
  };
  await metaPut(cacheKey(who.staffNumber), record);
}

function makeSession(
  who: Pick<StaffSession, "staffId" | "staffNumber" | "displayName" | "role" | "branchId">,
  via: StaffSession["via"],
  nowMs: number
): StaffSession {
  return { ...who, signedInAt: nowMs, expiresAt: nowMs + SESSION_TTL_MS, via };
}

async function signInOnDevice(staffNumber: string, pin: string, nowMs: number): Promise<SignInResult> {
  const cached = await metaGet<CachedStaff>(cacheKey(staffNumber));
  if (!cached) return { kind: "no-device-record" };
  if (cached.lockedUntil && nowMs < cached.lockedUntil) {
    return { kind: "locked", until: cached.lockedUntil, source: "device" };
  }
  const candidate = await derive(pin, fromHex(cached.saltHex), cached.iterations);
  if (constantTimeEqual(candidate, cached.verifierHex)) {
    await metaPut(cacheKey(staffNumber), { ...cached, failedAttempts: 0, lockedUntil: null });
    const session = makeSession(cached, "device", nowMs);
    await metaPut("session", session);
    return { kind: "signed-in", session };
  }
  const failed = cached.failedAttempts + 1;
  const lock = lockoutAfterFailure(failed);
  await metaPut(cacheKey(staffNumber), { ...cached, failedAttempts: failed, lockedUntil: lock ? nowMs + lock : null });
  return { kind: "refused" };
}

export interface SignInOptions {
  nowMs?: number;
  /** Test seam; defaults to the browser's fetch. */
  fetchImpl?: typeof fetch;
  /** Skip the server entirely (a known-offline till). */
  offline?: boolean;
}

export function signInMessage(terminalId: string, staffNumber: string, epochSecond: number): Uint8Array {
  return new TextEncoder().encode(`mara.staff-signin.v1|${terminalId}|${staffNumber}|${epochSecond}`);
}

export async function signInStaff(staffNumberRaw: string, pin: string, options: SignInOptions = {}): Promise<SignInResult> {
  const nowMs = options.nowMs ?? Date.now();
  const staffNumber = staffNumberRaw.trim();
  const identity = await getIdentity();
  if (!identity) return { kind: "not-enrolled" };

  const online = !options.offline && (typeof navigator === "undefined" || navigator.onLine !== false);
  if (online) {
    const epochSecond = Math.floor(nowMs / 1000);
    const signature = await signDigest(identity.privateKey, signInMessage(identity.terminalId, staffNumber, epochSecond));
    let response: Response | null = null;
    try {
      response = await (options.fetchImpl ?? fetch)("/api/signin", {
        method: "POST",
        headers: { "content-type": "application/json" },
        body: JSON.stringify({ terminalId: identity.terminalId, staffNumber, pin, timestamp: epochSecond, signature })
      });
    } catch {
      response = null;
    }
    // 502 is the terminal's own proxy saying identity-service did not answer: fall through
    // to the device path exactly as if the network were down.
    if (response && response.status !== 502) {
      const body = (await response.json().catch(() => null)) as Record<string, unknown> | null;
      if (response.status === 200 && body && typeof body.staffId === "string") {
        const who = {
          staffId: body.staffId,
          staffNumber: String(body.staffNumber ?? staffNumber),
          displayName: String(body.displayName ?? staffNumber),
          role: body.role as StaffRole,
          branchId: (body.branchId as string | null) ?? null
        };
        await remember(who, pin);
        const session = makeSession(who, "server", nowMs);
        await metaPut("session", session);
        return { kind: "signed-in", session };
      }
      if (response.status === 423 && body && typeof body.lockedUntil === "string") {
        return { kind: "locked", until: Date.parse(body.lockedUntil), source: "server" };
      }
      // The server said no: that is final, even if the device would have said yes (the
      // member of staff may have been suspended or the PIN changed since it was cached).
      await metaDelete(cacheKey(staffNumber));
      return { kind: "refused" };
    }
  }
  return signInOnDevice(staffNumber, pin, nowMs);
}

/** Roles that may override a sale. Mirrors platform StaffRole rank >= SUPERVISOR. */
export function canSupervise(role: StaffRole): boolean {
  return role !== "CASHIER";
}
