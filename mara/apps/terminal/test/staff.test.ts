import "fake-indexeddb/auto";
import { IDBFactory } from "fake-indexeddb";
import { beforeEach, describe, expect, it } from "vitest";
import { resetDbForTests } from "../src/lib/db";
import { generateTerminalKey, verifyDigest } from "../src/lib/keys";
import { getSession, lockoutAfterFailure, signInMessage, signInStaff, signOut } from "../src/lib/staff";
import { fromHex } from "../src/lib/bytes";
import { saveIdentityOnce } from "../src/lib/terminal-store";

const TERMINAL = "TERM-0123456789ABCDEF0123";
let spki = "";

beforeEach(async () => {
  (globalThis as any).indexedDB = new IDBFactory();
  resetDbForTests();
  const k = await generateTerminalKey();
  spki = k.publicKeySpkiBase64;
  await saveIdentityOnce({
    terminalId: TERMINAL, label: "Lane 1", publicKeySpkiBase64: k.publicKeySpkiBase64,
    privateKey: k.privateKey, publicKey: k.publicKey, enrolledAt: Date.now()
  });
});

const ok = (body: object, status = 200) => async () => new Response(JSON.stringify(body), { status });
const WHO = { staffId: "STF-1", staffNumber: "2001", displayName: "Wanjiru", role: "CASHIER", branchId: "BR-1" };

describe("staff sign-in", () => {
  it("lockout doubles from the sixth failure and is capped at thirty minutes", () => {
    expect([1, 4].map(lockoutAfterFailure)).toEqual([0, 0]);
    expect([5, 6, 7, 8].map(lockoutAfterFailure)).toEqual([60_000, 120_000, 240_000, 480_000]);
    expect(lockoutAfterFailure(50)).toBe(30 * 60_000);
  });

  it("signs the attempt with the terminal key over terminal, staff number and time", async () => {
    let sent: any;
    const fetchImpl = (async (_u: string, init: RequestInit) => {
      sent = JSON.parse(String(init.body));
      return new Response(JSON.stringify(WHO), { status: 200 });
    }) as unknown as typeof fetch;
    const r = await signInStaff("2001", "4826", { fetchImpl, nowMs: 1_700_000_000_000 });
    expect(r.kind).toBe("signed-in");
    expect(sent.terminalId).toBe(TERMINAL);
    expect(sent.timestamp).toBe(1_700_000_000);
    expect(await verifyDigest(spki, signInMessage(TERMINAL, "2001", 1_700_000_000), sent.signature)).toBe(true);
    // The signature does not transfer to another staff number.
    expect(await verifyDigest(spki, signInMessage(TERMINAL, "2002", 1_700_000_000), sent.signature)).toBe(false);
    expect(fromHex(sent.signature).length).toBe(64);
  });

  it("a server refusal is final and never falls back to the device", async () => {
    await signInStaff("2001", "4826", { fetchImpl: ok(WHO) as any });
    await signOut();
    const refused = await signInStaff("2001", "4826", { fetchImpl: ok({ error: "signin_refused" }, 401) as any });
    expect(refused.kind).toBe("refused");
    // The cached verifier was dropped, so offline cannot resurrect a suspended person.
    expect((await signInStaff("2001", "4826", { offline: true })).kind).toBe("no-device-record");
  });

  it("after one online sign-in the same PIN works offline, and a wrong one does not", async () => {
    await signInStaff("2001", "4826", { fetchImpl: ok(WHO) as any });
    await signOut();
    const offline = await signInStaff("2001", "4826", { offline: true });
    expect(offline.kind).toBe("signed-in");
    expect(offline.kind === "signed-in" && offline.session.via).toBe("device");
    expect((await getSession())?.displayName).toBe("Wanjiru");
    await signOut();
    expect((await signInStaff("2001", "0000", { offline: true })).kind).toBe("refused");
  });

  it("an unreachable identity-service (502) behaves like being offline", async () => {
    await signInStaff("2001", "4826", { fetchImpl: ok(WHO) as any });
    await signOut();
    const r = await signInStaff("2001", "4826", { fetchImpl: ok({ error: "identity_unreachable" }, 502) as any });
    expect(r.kind).toBe("signed-in");
  });

  it("five wrong PINs offline lock the device, and the lock outlasts the right PIN", async () => {
    await signInStaff("2001", "4826", { fetchImpl: ok(WHO) as any });
    await signOut();
    const t0 = 1_800_000_000_000;
    for (let i = 0; i < 5; i++) await signInStaff("2001", "1111", { offline: true, nowMs: t0 + i });
    const locked = await signInStaff("2001", "4826", { offline: true, nowMs: t0 + 10 });
    expect(locked.kind).toBe("locked");
    const later = await signInStaff("2001", "4826", { offline: true, nowMs: t0 + 61_000 });
    expect(later.kind).toBe("signed-in");
  });

  it("the server's lockout is reported with its time", async () => {
    const until = new Date(Date.now() + 120_000).toISOString();
    const r = await signInStaff("2001", "4826", { fetchImpl: ok({ error: "locked", lockedUntil: until }, 423) as any });
    expect(r).toEqual({ kind: "locked", until: Date.parse(until), source: "server" });
  });

  it("the PIN is never stored, and a session expires", async () => {
    await signInStaff("2001", "4826", { fetchImpl: ok(WHO) as any, nowMs: 1_000 });
    const all = JSON.stringify(await (async () => {
      const db = await (await import("../src/lib/db")).openDb();
      const tx = db.transaction("meta");
      const out: unknown[] = [];
      await new Promise<void>((res) => {
        tx.objectStore("meta").openCursor().onsuccess = (e: any) => {
          const c = e.target.result;
          if (c) { out.push(c.value); c.continue(); } else res();
        };
      });
      return out;
    })());
    expect(all).not.toContain("4826");
    expect(await getSession(1_000 + 13 * 3600_000)).toBeNull();
  });
});
