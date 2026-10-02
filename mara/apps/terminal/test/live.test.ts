/**
 * Drives the REAL stack (docker compose) with the terminal's own libraries: real WebCrypto
 * Ed25519 keys, real identity-service, real Postgres. Skipped unless LIVE_TERMINAL_URL and
 * LIVE_ENROL_CODE are set, e.g.
 *   LIVE_TERMINAL_URL=http://localhost:13100 LIVE_ENROL_CODE=ABCDE-FGHJK npx vitest run test/live.test.ts
 * It proves what unit tests cannot: that a signature made in TypeScript verifies in Java.
 */
import "fake-indexeddb/auto";
import { IDBFactory } from "fake-indexeddb";
import { beforeAll, describe, expect, it } from "vitest";
import { loadDemoCatalogue } from "../src/lib/demo-catalogue";
import { listItems } from "../src/lib/catalogue-store";
import { resetDbForTests } from "../src/lib/db";
import { enrol } from "../src/lib/enrol";
import { getHead, readAscending } from "../src/lib/journal-store";
import { recordSale } from "../src/lib/record-sale";
import { getSession, signInStaff, signOut } from "../src/lib/staff";
import { summariseJournal } from "../src/lib/summary";
import { addToTab, openTab } from "../src/lib/tab-store";
import { getIdentity } from "../src/lib/terminal-store";
import { verifyLocalJournal } from "../src/lib/verify-journal";
import { getLease } from "../src/lib/journal-store";
import { getPendingReturns, getSyncState, syncCycle } from "../src/lib/sync";

const BASE = process.env.LIVE_TERMINAL_URL;
const CODE = process.env.LIVE_ENROL_CODE;
const live = BASE && CODE ? describe : describe.skip;

// Optional: with these set the sync test also reads the servers' own books back.
const ADMIN = process.env.LIVE_ADMIN_TOKEN;
const TENANT = process.env.LIVE_TENANT;
const SYNC_URL = process.env.LIVE_SYNC_URL;
const CORE_URL = process.env.LIVE_CORE_URL;
const backOffice = ADMIN && TENANT && SYNC_URL && CORE_URL ? it : it.skip;

const realFetch = globalThis.fetch;
const viaTerminal = ((input: RequestInfo | URL, init?: RequestInit) =>
  realFetch(typeof input === "string" && input.startsWith("/") ? BASE + input : input, init)) as typeof fetch;

live("the till against the real stack", () => {
  beforeAll(() => {
    (globalThis as any).indexedDB = new IDBFactory();
    resetDbForTests();
    globalThis.fetch = viaTerminal;
    // Node's navigator has no onLine; a browser on a working network reports true.
    Object.defineProperty(globalThis.navigator, "onLine", { value: true, configurable: true });
  });

  it("enrols, signs staff in (Java verifies our Ed25519 signature), sells, and the journal verifies", async () => {
    const enrolled = await enrol(CODE!, "Live test lane");
    expect(enrolled.kind).toBe("enrolled");
    const id = (await getIdentity())!;
    expect(id.terminalId).toMatch(/^TERM-[0-9A-F]{20}$/);

    // a second use of the same code is refused
    // (a fresh browser would be needed to try; covered by the Java suite)

    // wrong PIN, then the right one
    expect((await signInStaff("2001", "0000")).kind).toBe("refused");
    const ok = await signInStaff("2001", "4826");
    expect(ok.kind).toBe("signed-in");
    expect(ok.kind === "signed-in" && ok.session.via).toBe("server");
    expect(ok.kind === "signed-in" && ok.session.displayName).toBe("Wanjiru Kamau");

    await loadDemoCatalogue();
    const s = (await getSession())!;
    const sugar = (await listItems({ q: "sugar", limit: 1 })).items[0];
    const tab = await openTab("live");
    await addToTab(tab.id, sugar);
    const rec = await recordSale({
      tabId: tab.id,
      cashier: { staffId: s.staffId, staffNumber: s.staffNumber, name: s.displayName },
      payments: [{ method: "MOBILE_MONEY", tenderedMinor: 18560n, reference: "MOCK-LIVE" }]
    });
    expect(rec.sale.totalMinor).toBe("18560");
    const report = await verifyLocalJournal({
      terminalId: id.terminalId, publicKeySpkiBase64: id.publicKeySpkiBase64,
      source: { head: getHead, page: readAscending }, pageSize: 10
    });
    expect(report.breaks).toEqual([]);
    expect((await summariseJournal()).days[0].mobileMinor).toBe(18560n);

    // offline afterwards: same PIN works from the device verifier
    await signOut();
    const offline = await signInStaff("2001", "4826", { offline: true });
    expect(offline.kind === "signed-in" && offline.session.via).toBe("device");
  });

  it("the simulated M-Pesa prompt succeeds, declines on a 00 number, and rejects junk", async () => {
    const good = await (await viaTerminal("/api/mpesa-mock", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ phone: "0712345678", amountMinor: "18560" }) })).json();
    expect(good).toMatchObject({ mock: true, status: "SUCCESS" });
    expect(good.receipt).toMatch(/^MOCK[0-9A-F]{8}$/);
    const declined = await (await viaTerminal("/api/mpesa-mock", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ phone: "0712345600", amountMinor: "100" }) })).json();
    expect(declined.status).toBe("CANCELLED");
    const bad = await viaTerminal("/api/mpesa-mock", { method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify({ phone: "123", amountMinor: "100" }) });
    expect(bad.status).toBe(400);
  });

  it("five wrong PINs lock the real account", async () => {
    for (let i = 0; i < 5; i++) await signInStaff("2002", "1111");
    const r = await signInStaff("2002", "6159");
    expect(r.kind).toBe("locked");
  });

  it("leases fiscal numbers, uploads the journal, and the servers' books agree with the till", async () => {
    const id = (await getIdentity())!;
    // Three more sales so there is something numbered and something pending.
    await signInStaff("2001", "4826");
    const s = (await getSession())!;
    const sugar = (await listItems({ q: "sugar", limit: 1 })).items[0];
    for (let i = 0; i < 2; i++) {
      const tab = await openTab("sync");
      await addToTab(tab.id, sugar);
      await recordSale({
        tabId: tab.id,
        cashier: { staffId: s.staffId, staffNumber: s.staffNumber, name: s.displayName },
        payments: [{ method: "CASH", tenderedMinor: 20000n, reference: "" }]
      });
    }
    const head = (await getHead())!;

    const state = await syncCycle();
    expect(state?.lastError).toBeNull();
    expect(state?.syncedThrough).toBe(head.lastSequence);
    const lease = await getLease();
    expect(lease?.leaseId).toBeTruthy();

    // After a lease exists, new sales are numbered from it.
    const tab = await openTab("numbered");
    await addToTab(tab.id, sugar);
    const numbered = await recordSale({
      tabId: tab.id,
      cashier: { staffId: s.staffId, staffNumber: s.staffNumber, name: s.displayName },
      payments: [{ method: "CASH", tenderedMinor: 20000n, reference: "" }]
    });
    expect(numbered.sale.fiscal.status).toBe("NUMBERED");
    expect(BigInt(numbered.sale.fiscal.number)).toBe(BigInt(lease!.firstNumber));
    expect((await syncCycle())?.syncedThrough).toBe(numbered.sequence);
    expect(await getPendingReturns()).toEqual([]);
    expect((await getSyncState()).heldAtGap).toBe(false);
    expect(id.terminalId).toBeTruthy();
  });

  backOffice("the sync service holds the verified chain and the core ledger balances", async () => {
    const id = (await getIdentity())!;
    const head = (await getHead())!;
    const h = { Authorization: `Bearer ${ADMIN}`, "X-Mara-Tenant": TENANT! };
    const chains = (await (await realFetch(`${SYNC_URL}/v1/admin/chains`, { headers: h })).json()) as { terminalId: string; lastSequence: number; headDigest: string }[];
    const mine = chains.find((c) => c.terminalId === id.terminalId)!;
    expect(mine.lastSequence).toBe(head.lastSequence);
    expect(mine.headDigest).toBe(head.headDigest);
    const exceptions = (await (await realFetch(`${SYNC_URL}/v1/admin/exceptions`, { headers: h })).json()) as { kind: string }[];
    expect(exceptions.filter((e) => e.kind !== "SALE_INCONSISTENT")).toEqual([]);

    // Ask core to pull now rather than waiting for its poll.
    await realFetch(`${CORE_URL}/v1/admin/ingest/run`, { method: "POST", headers: h });
    const tb = (await (await realFetch(`${CORE_URL}/v1/admin/ledger/trial-balance`, { headers: h })).json()) as { debitMinor: number; creditMinor: number }[];
    expect(tb.reduce((n, r) => n + Number(r.debitMinor), 0)).toBe(tb.reduce((n, r) => n + Number(r.creditMinor), 0));
    expect(tb.length).toBeGreaterThan(0);
  });
});
