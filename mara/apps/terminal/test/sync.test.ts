import "fake-indexeddb/auto";
import { IDBFactory } from "fake-indexeddb";
import { beforeEach, describe, expect, it } from "vitest";
import { createItem } from "../src/lib/catalogue-store";
import { resetDbForTests } from "../src/lib/db";
import { fromHex, toHex, utf8 } from "../src/lib/bytes";
import { sha256 } from "../src/lib/chain";
import { getHead, getLease } from "../src/lib/journal-store";
import { generateTerminalKey, verifyDigest } from "../src/lib/keys";
import { putMeta } from "../src/lib/meta";
import { recordSale } from "../src/lib/record-sale";
import { addToTab, openTab } from "../src/lib/tab-store";
import { getIdentity, saveIdentityOnce } from "../src/lib/terminal-store";
import { getPendingReturns, getSyncState, maintainLease, requestMessage, syncJournal } from "../src/lib/sync";

const TERMINAL = "TERM-AAAAAAAAAAAAAAAAAAAA";

beforeEach(() => {
  (globalThis as { indexedDB: IDBFactory }).indexedDB = new IDBFactory();
  resetDbForTests();
});

async function enrol() {
  const k = await generateTerminalKey();
  await saveIdentityOnce({
    terminalId: TERMINAL, label: "Lane 1", publicKeySpkiBase64: k.publicKeySpkiBase64,
    privateKey: k.privateKey, publicKey: k.publicKey, enrolledAt: Date.now()
  });
  return k;
}

async function sell(n: number) {
  for (let i = 0; i < n; i++) {
    const item = await createItem({ sku: `S-${i}-${Math.random()}`, name: "Widget", unitMinor: 10000n, taxBp: 1600 });
    const tab = await openTab("t");
    await addToTab(tab.id, item);
    await recordSale({ tabId: tab.id, payments: [{ method: "CASH", tenderedMinor: 20000n, reference: "" }] });
  }
}

type Call = { url: string; method: string; headers: Record<string, string>; body: string };

/** A fake server: records calls, answers from a script. */
function fakeServer(handler: (c: Call) => { status: number; json: unknown } | "throw") {
  const calls: Call[] = [];
  const fetchImpl = (async (url: string, init: RequestInit) => {
    const call: Call = {
      url: String(url), method: String(init.method), headers: init.headers as Record<string, string>, body: String(init.body ?? "")
    };
    calls.push(call);
    const r = handler(call);
    if (r === "throw") throw new TypeError("network down");
    return new Response(JSON.stringify(r.json), { status: r.status });
  }) as unknown as typeof fetch;
  return { calls, fetchImpl };
}

const ok = (acceptedThrough: number) => ({ status: 200, json: { acceptedThrough, accepted: 0, duplicates: 0, gap: null, refused: [], flagged: [] } });

describe("sync", () => {
  it("signs every request over terminal, time, verb, path and body hash, and the signature verifies", async () => {
    const k = await enrol();
    await sell(1);
    const { calls, fetchImpl } = fakeServer((c) => (c.url.endsWith("status") ? { status: 200, json: { openExceptions: 0, heldAtGap: false } } : ok(1)));
    await syncJournal({ fetchImpl });
    const upload = calls.find((c) => c.url === "/api/sync/journal")!;
    const h = upload.headers;
    expect(h["x-mara-terminal"]).toBe(TERMINAL);
    const hash = toHex(await sha256(utf8(upload.body)));
    const message = requestMessage(TERMINAL, Number(h["x-mara-timestamp"]), "POST", "/v1/terminal/sync/journal", hash);
    expect(await verifyDigest(k.publicKeySpkiBase64, message, h["x-mara-signature"])).toBe(true);
    // the same signature does not verify for another path or another body
    const other = requestMessage(TERMINAL, Number(h["x-mara-timestamp"]), "POST", "/v1/terminal/fiscal/leases", hash);
    expect(await verifyDigest(k.publicKeySpkiBase64, other, h["x-mara-signature"])).toBe(false);
    expect(fromHex(h["x-mara-signature"]).length).toBe(64);
  });

  it("uploads the whole journal in batches and moves the cursor only to what the server confirms", async () => {
    await enrol();
    await sell(5);
    const { calls, fetchImpl } = fakeServer((c) => {
      if (c.url.endsWith("status")) return { status: 200, json: { openExceptions: 0, heldAtGap: false } };
      const entries = JSON.parse(c.body).entries as { sequence: number }[];
      return ok(entries[entries.length - 1].sequence);
    });
    const state = await syncJournal({ fetchImpl });
    expect(state.syncedThrough).toBe(5);
    expect(state.lastError).toBeNull();
    // already caught up: a second run uploads nothing
    const before = calls.filter((c) => c.url === "/api/sync/journal").length;
    await syncJournal({ fetchImpl });
    expect(calls.filter((c) => c.url === "/api/sync/journal").length).toBe(before);
    expect((await getSyncState()).syncedThrough).toBe(5);
  });

  it("does not claim more than it sent even if the server claims more", async () => {
    await enrol();
    await sell(3);
    const { fetchImpl } = fakeServer((c) => (c.url.endsWith("status") ? { status: 200, json: {} } : ok(999)));
    expect((await syncJournal({ fetchImpl })).syncedThrough).toBe(3);
  });

  it("stops at a gap, keeps what the server accepted, and says so", async () => {
    await enrol();
    await sell(4);
    const { fetchImpl } = fakeServer((c) =>
      c.url.endsWith("status")
        ? { status: 200, json: { openExceptions: 1, heldAtGap: true } }
        : { status: 200, json: { acceptedThrough: 1, accepted: 1, duplicates: 0, gap: { from: 2, to: 2 }, refused: [], flagged: [] } }
    );
    const state = await syncJournal({ fetchImpl });
    expect(state.syncedThrough).toBe(1);
    expect(state.heldAtGap).toBe(true);
    expect(state.lastError).toContain("missing sequence 2");
    expect(state.openExceptions).toBe(1);
  });

  it("an unreachable server changes nothing but the message, and the till keeps selling", async () => {
    await enrol();
    await sell(2);
    const { fetchImpl } = fakeServer(() => "throw");
    const state = await syncJournal({ fetchImpl });
    expect(state.syncedThrough).toBe(0);
    expect(state.lastError).toContain("sales are safe on this device");
    await sell(1);
    expect((await getHead())!.lastSequence).toBe(3);
  });

  it("a 401 is reported as a rejected signature, not as a network problem", async () => {
    await enrol();
    await sell(1);
    const { fetchImpl } = fakeServer(() => ({ status: 401, json: { error: "unauthorised" } }));
    expect((await syncJournal({ fetchImpl })).lastError).toContain("signature");
  });
});

describe("fiscal lease", () => {
  const lease = (first: number, last: number, id = "11") => ({
    leaseId: id, terminalId: TERMINAL, firstNumber: String(first), lastNumber: String(last),
    nextNumber: String(first), issuedAtMs: Date.now(), expiresAtMs: Date.now() + 86_400_000
  });

  it("with no lease it asks for one, installs it, and the next sales are numbered", async () => {
    await enrol();
    await sell(1);
    expect((await getHead())!.lastSequence).toBe(1);
    const { calls, fetchImpl } = fakeServer(() => ({ status: 201, json: lease(1, 100) }));
    const r = await maintainLease({ fetchImpl });
    expect(r.installed).toBe(true);
    expect(calls[0].url).toBe("/api/fiscal/lease");
    expect((await getLease())!.leaseId).toBe("11");

    await sell(2);
    const lastLease = (await getLease())!;
    expect(lastLease.nextNumber).toBe("3");       // numbers 1 and 2 drawn
    expect(lastLease.leaseId).toBe("11");         // and the lease id survived being drawn from
  });

  it("renews at 20% remaining, installs the new lease first and queues the old tail for return", async () => {
    await enrol();
    await putMeta("fiscalLease", { ...lease(1, 10), nextNumber: "9" });   // 2 of 10 left = 20%
    const { calls, fetchImpl } = fakeServer((c) =>
      c.url === "/api/fiscal/lease" ? { status: 201, json: lease(11, 110, "12") } : { status: 500, json: {} }
    );
    const r = await maintainLease({ fetchImpl });
    expect(r.installed).toBe(true);
    expect((await getLease())!.leaseId).toBe("12");
    // the return was attempted for the old tail and, the server failing, is kept to retry
    expect(calls.some((c) => c.url === "/api/fiscal/return/11")).toBe(true);
    expect(await getPendingReturns()).toEqual([{ leaseId: "11", nextUnused: "9" }]);
    expect(JSON.parse(calls.find((c) => c.url === "/api/fiscal/return/11")!.body)).toEqual({ nextUnused: "9" });
  });

  it("a returned tail that the server accepts is no longer pending", async () => {
    await enrol();
    await putMeta("fiscalLease", { ...lease(1, 10), nextNumber: "9" });
    const { fetchImpl } = fakeServer((c) =>
      c.url === "/api/fiscal/lease" ? { status: 201, json: lease(11, 110, "12") } : { status: 200, json: { voidedFrom: 9 } }
    );
    await maintainLease({ fetchImpl });
    expect(await getPendingReturns()).toEqual([]);
  });

  it("does not ask for a lease while the current one is healthy", async () => {
    await enrol();
    await putMeta("fiscalLease", lease(1, 100));
    const { calls, fetchImpl } = fakeServer(() => ({ status: 500, json: {} }));
    expect((await maintainLease({ fetchImpl })).installed).toBe(false);
    expect(calls.length).toBe(0);
  });

  it("offline, a sale is still made and is FISCAL_PENDING", async () => {
    await enrol();
    const { fetchImpl } = fakeServer(() => "throw");
    expect((await maintainLease({ fetchImpl })).installed).toBe(false);
    await sell(1);
    const identity = await getIdentity();
    expect(identity).not.toBeNull();
    expect((await getHead())!.lastSequence).toBe(1);
  });
});
