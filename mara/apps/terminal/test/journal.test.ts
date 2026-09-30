import "fake-indexeddb/auto";
import { IDBFactory } from "fake-indexeddb";
import { beforeEach, describe, expect, it } from "vitest";
import { createItem } from "../src/lib/catalogue-store";
import { openDb, resetDbForTests, STORE, wrap } from "../src/lib/db";
import { generateTerminalKey } from "../src/lib/keys";
import { commitSale, getHead, getLease, listEntries, readAscending, countEntries } from "../src/lib/journal-store";
import { NotEnrolledError, recordSale, SaleRefusedError } from "../src/lib/record-sale";
import type { JournalRecord, Tab } from "../src/lib/records";
import { addToTab, getTab, openTab } from "../src/lib/tab-store";
import { getIdentity, saveIdentityOnce } from "../src/lib/terminal-store";
import { findings, type LocalReport, verifyLocalJournal } from "../src/lib/verify-journal";
import { toHex } from "../src/lib/bytes";
import { generateTerminalKey as gen } from "../src/lib/keys";

const TERMINAL = "TERM-TEST0001";

async function enrol() {
  const k = await generateTerminalKey();
  await saveIdentityOnce({
    terminalId: TERMINAL, label: "Lane 1", publicKeySpkiBase64: k.publicKeySpkiBase64,
    privateKey: k.privateKey, publicKey: k.publicKey, enrolledAt: Date.now()
  });
  return k;
}

async function sellOne(unit = 15000n, when?: number): Promise<JournalRecord> {
  const item = await createItem({ sku: `S-${Math.random()}`, name: "Widget", unitMinor: unit, taxBp: 1600 });
  const tab = await openTab("t");
  await addToTab(tab.id, item);
  return recordSale({
    tabId: tab.id,
    payments: [{ method: "CASH", tenderedMinor: 100000n, reference: "" }],
    nowMs: when
  });
}

async function verifyAll(): Promise<LocalReport> {
  const id = (await getIdentity())!;
  return verifyLocalJournal({
    terminalId: id.terminalId,
    publicKeySpkiBase64: id.publicKeySpkiBase64,
    source: { head: getHead, page: readAscending },
    pageSize: 4
  });
}

async function rawStore<T>(store: string, fn: (s: IDBObjectStore) => IDBRequest<T>, mode: IDBTransactionMode = "readwrite") {
  const db = await openDb();
  const tx = db.transaction(store, mode);
  const r = await wrap(fn(tx.objectStore(store)));
  await new Promise<void>((res) => (tx.oncomplete = () => res()));
  return r;
}

beforeEach(() => {
  (globalThis as any).indexedDB = new IDBFactory();
  resetDbForTests();
});

describe("selling", () => {
  it("refuses to sell before enrolment", async () => {
    const item = await createItem({ sku: "A", name: "A", unitMinor: 100n, taxBp: 0 });
    const tab = await openTab("");
    await addToTab(tab.id, item);
    await expect(recordSale({ tabId: tab.id, payments: [{ method: "CASH", tenderedMinor: 100n, reference: "" }] })).rejects.toBeInstanceOf(NotEnrolledError);
    expect(await countEntries()).toBe(0);
  });

  it("records FISCAL_PENDING when there is no lease, chained from genesis, and closes the tab", async () => {
    await enrol();
    const e1 = await sellOne();
    expect(e1.sequence).toBe(1);
    expect(e1.previousDigest).toBe("0".repeat(64));
    expect(e1.sale.fiscal).toEqual({ status: "FISCAL_PENDING", number: "" });
    expect(e1.sale.totalMinor).toBe("17400");   // 15000 + 16%
    const e2 = await sellOne();
    expect(e2.previousDigest).toBe(e1.digest);
    expect((await getHead())!.headDigest).toBe(e2.digest);
    expect((await getHead())!.genesisDigest).toBe(e1.digest);
  });

  it("does not sell the same tab twice, and refuses part-paid or empty tabs", async () => {
    await enrol();
    const item = await createItem({ sku: "A", name: "A", unitMinor: 1000n, taxBp: 0 });
    const tab = await openTab("");
    await expect(recordSale({ tabId: tab.id, payments: [{ method: "CASH", tenderedMinor: 1000n, reference: "" }] })).rejects.toBeInstanceOf(SaleRefusedError);
    await addToTab(tab.id, item);
    await expect(recordSale({ tabId: tab.id, payments: [{ method: "CASH", tenderedMinor: 999n, reference: "" }] })).rejects.toBeInstanceOf(SaleRefusedError);
    await recordSale({ tabId: tab.id, payments: [{ method: "CASH", tenderedMinor: 1000n, reference: "" }] });
    await expect(recordSale({ tabId: tab.id, payments: [{ method: "CASH", tenderedMinor: 1000n, reference: "" }] })).rejects.toBeInstanceOf(SaleRefusedError);
    expect(await countEntries()).toBe(1);
    expect(await getTab(tab.id)).toBeNull();
  });

  it("concurrent sales get strictly monotonic, unique sequences with an unbroken chain", async () => {
    await enrol();
    const tabs: Tab[] = [];
    for (let i = 0; i < 8; i++) {
      const item = await createItem({ sku: `C${i}`, name: `C${i}`, unitMinor: 500n + BigInt(i), taxBp: 1600 });
      const t = await openTab(`t${i}`);
      await addToTab(t.id, item);
      tabs.push(t);
    }
    const out = await Promise.all(tabs.map((t) => recordSale({ tabId: t.id, payments: [{ method: "CASH", tenderedMinor: 10000n, reference: "" }] })));
    expect(out.map((r) => r.sequence).sort((a, b) => a - b)).toEqual([1, 2, 3, 4, 5, 6, 7, 8]);
    const report = await verifyAll();
    expect(findings(report)).toEqual([{ kind: "intact", entries: 8 }]);
  });

  it("the transaction itself refuses a stale writer even without the cross-tab lock", async () => {
    await enrol();
    const first = await sellOne();
    const head = (await getHead())!;
    const clone: JournalRecord = { ...first, sequence: 2 };
    await expect(
      commitSale({ record: clone, head: { ...head, lastSequence: 2 }, expectedPreviousSequence: 0, expectedLeaseNext: null, lease: null, tabId: null })
    ).rejects.toThrow(/changed while/);
    expect(await countEntries()).toBe(1);
  });

  it("numbers from a lease when one exists, advances it atomically, then degrades to pending", async () => {
    await enrol();
    const now = Date.now();
    await rawStore(STORE.meta, (s) =>
      s.put({ terminalId: TERMINAL, firstNumber: "500", lastNumber: "501", nextNumber: "500", issuedAtMs: now - 1000, expiresAtMs: now + 3_600_000 }, "fiscalLease")
    );
    const a = await sellOne();
    const b = await sellOne();
    const c = await sellOne();
    expect(a.sale.fiscal).toEqual({ status: "NUMBERED", number: "500" });
    expect(b.sale.fiscal).toEqual({ status: "NUMBERED", number: "501" });
    expect(c.sale.fiscal.status).toBe("FISCAL_PENDING");
    expect((await getLease())!.nextNumber).toBe("502");
  });

  it("clamps a clock that ran backwards instead of refusing the sale", async () => {
    await enrol();
    const a = await sellOne(1000n, 2_000_000_000_000);
    const b = await sellOne(1000n, 1_000_000_000_000);
    expect(b.epochSecond).toBeGreaterThanOrEqual(a.epochSecond);
    expect(findings(await verifyAll())).toEqual([{ kind: "intact", entries: 2 }]);
  });
});

describe("paging", () => {
  it("lists the journal newest-first by keyset, one page at a time", async () => {
    await enrol();
    for (let i = 0; i < 7; i++) await sellOne();
    const p1 = await listEntries({ limit: 3 });
    expect(p1.items.map((e) => e.sequence)).toEqual([7, 6, 5]);
    const p2 = await listEntries({ before: p1.next, limit: 3 });
    expect(p2.items.map((e) => e.sequence)).toEqual([4, 3, 2]);
    const p3 = await listEntries({ before: p2.next, limit: 3 });
    expect(p3.items.map((e) => e.sequence)).toEqual([1]);
    expect(p3.next).toBeNull();
  });
});

describe("verification reports findings, never a single boolean", () => {
  async function build(n: number) {
    await enrol();
    for (let i = 0; i < n; i++) await sellOne(1000n + BigInt(i));
  }
  const first = (r: LocalReport) => findings(r)[0];

  it("intact chain", async () => {
    await build(10);
    expect(findings(await verifyAll())).toEqual([{ kind: "intact", entries: 10 }]);
  });

  it("empty journal is reported as empty, not as intact", async () => {
    await enrol();
    expect(findings(await verifyAll())).toEqual([{ kind: "empty" }]);
  });

  it("EDITED sale content is caught at the edited entry", async () => {
    await build(6);
    const rec = (await rawStore(STORE.journal, (s) => s.get(3))) as JournalRecord;
    rec.sale.lines[0].qty = 9;
    await rawStore(STORE.journal, (s) => s.put(rec));
    const f = first(await verifyAll());
    expect(f).toMatchObject({ kind: "broken", sequence: 3, reason: "BODY_MISMATCH" });
  });

  it("edited content with the body digest recomputed to match is still caught (digest or link breaks)", async () => {
    await build(6);
    const { saleBodyDigest } = await import("../src/lib/sale");
    const rec = (await rawStore(STORE.journal, (s) => s.get(3))) as JournalRecord;
    rec.sale.totalMinor = "1";
    rec.bodyDigest = toHex(await saleBodyDigest(rec.sale));
    await rawStore(STORE.journal, (s) => s.put(rec));
    const f = first(await verifyAll());
    expect(f).toMatchObject({ kind: "broken", sequence: 3, reason: "DIGEST_MISMATCH" });
  });

  it("a fully re-hashed forgery of one entry breaks the link of the next", async () => {
    await build(6);
    const { saleBodyDigest } = await import("../src/lib/sale");
    const { chainDigest } = await import("../src/lib/chain");
    const { fromHex } = await import("../src/lib/bytes");
    const rec = (await rawStore(STORE.journal, (s) => s.get(3))) as JournalRecord;
    rec.sale.totalMinor = "1";
    rec.bodyDigest = toHex(await saleBodyDigest(rec.sale));
    rec.digest = toHex(
      await chainDigest({
        terminalId: rec.terminalId, sequence: 3n, epochSecond: BigInt(rec.epochSecond), nano: rec.nano,
        bodyDigest: fromHex(rec.bodyDigest), previousDigest: fromHex(rec.previousDigest)
      })
    );
    await rawStore(STORE.journal, (s) => s.put(rec));
    const r = await verifyAll();
    // entry 3 no longer carries the owner's signature, and entry 4 no longer chains onto it
    expect(first(r)).toMatchObject({ kind: "broken", sequence: 3, reason: "BAD_SIGNATURE" });
  });

  it("a forgery re-signed with a different key fails the signature check", async () => {
    await build(4);
    const { saleBodyDigest } = await import("../src/lib/sale");
    const { chainDigest } = await import("../src/lib/chain");
    const { fromHex } = await import("../src/lib/bytes");
    const { signDigest } = await import("../src/lib/keys");
    const attacker = await gen();
    const rec = (await rawStore(STORE.journal, (s) => s.get(4))) as JournalRecord;
    rec.sale.totalMinor = "1";
    rec.bodyDigest = toHex(await saleBodyDigest(rec.sale));
    const d = await chainDigest({
      terminalId: rec.terminalId, sequence: 4n, epochSecond: BigInt(rec.epochSecond), nano: rec.nano,
      bodyDigest: fromHex(rec.bodyDigest), previousDigest: fromHex(rec.previousDigest)
    });
    rec.digest = toHex(d);
    rec.signature = await signDigest(attacker.privateKey, d);
    await rawStore(STORE.journal, (s) => s.put(rec));
    expect(first(await verifyAll())).toMatchObject({ kind: "broken", sequence: 4, reason: "BAD_SIGNATURE" });
  });

  it("a deleted middle entry is a GAP at exactly that sequence", async () => {
    await build(9);
    await rawStore(STORE.journal, (s) => s.delete(5));
    const r = await verifyAll();
    expect(findings(r)).toEqual([{ kind: "gap", fromSequence: 5, toSequence: 5, where: "inside" }]);
    expect(r.verifiedThrough).toBe(4);
  });

  it("a deleted run of entries names the whole run", async () => {
    await build(12);
    for (const s of [4, 5, 6, 7]) await rawStore(STORE.journal, (st) => st.delete(s));
    expect(findings(await verifyAll())).toEqual([{ kind: "gap", fromSequence: 4, toSequence: 7, where: "inside" }]);
  });

  it("a deleted first entry is a gap from 1", async () => {
    await build(5);
    await rawStore(STORE.journal, (s) => s.delete(1));
    expect(findings(await verifyAll())).toEqual([{ kind: "gap", fromSequence: 1, toSequence: 1, where: "inside" }]);
  });

  it("entries deleted from the END are caught by the head record", async () => {
    await build(8);
    await rawStore(STORE.journal, (s) => s.delete(8));
    await rawStore(STORE.journal, (s) => s.delete(7));
    expect(findings(await verifyAll())).toEqual([{ kind: "gap", fromSequence: 7, toSequence: 8, where: "tail" }]);
  });

  it("reordering: swapping two entries' sequence numbers breaks the chain", async () => {
    await build(6);
    const a = (await rawStore(STORE.journal, (s) => s.get(2))) as JournalRecord;
    const b = (await rawStore(STORE.journal, (s) => s.get(3))) as JournalRecord;
    await rawStore(STORE.journal, (s) => s.put({ ...a, sequence: 3 }));
    await rawStore(STORE.journal, (s) => s.put({ ...b, sequence: 2 }));
    const f = first(await verifyAll());
    expect(f.kind).toBe("broken");
    expect(f).toMatchObject({ sequence: 2, reason: "BROKEN_LINK" });
  });

  it("a corrupted signature is caught", async () => {
    await build(3);
    const rec = (await rawStore(STORE.journal, (s) => s.get(2))) as JournalRecord;
    rec.signature = (rec.signature[0] === "a" ? "b" : "a") + rec.signature.slice(1);
    await rawStore(STORE.journal, (s) => s.put(rec));
    expect(first(await verifyAll())).toMatchObject({ kind: "broken", sequence: 2, reason: "BAD_SIGNATURE" });
  });

  it("entries surviving after their head record was replaced are flagged", async () => {
    await build(4);
    const head = (await getHead())!;
    await rawStore(STORE.meta, (s) => s.put({ ...head, headDigest: "ab".repeat(32) }, "journalHead"));
    expect(first(await verifyAll())).toMatchObject({ kind: "broken", reason: "HEAD_MISMATCH" });
  });

  it("pages of one entry give the same answer as one big page", async () => {
    await build(9);
    const id = (await getIdentity())!;
    const small = await verifyLocalJournal({ terminalId: id.terminalId, publicKeySpkiBase64: id.publicKeySpkiBase64, source: { head: getHead, page: readAscending }, pageSize: 1 });
    const big = await verifyLocalJournal({ terminalId: id.terminalId, publicKeySpkiBase64: id.publicKeySpkiBase64, source: { head: getHead, page: readAscending }, pageSize: 500 });
    expect(small).toEqual(big);
  });
});
