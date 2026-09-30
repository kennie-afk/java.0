import "fake-indexeddb/auto";
import { IDBFactory } from "fake-indexeddb";
import { beforeEach, describe, expect, it } from "vitest";
import { DEMO_ITEMS, loadDemoCatalogue } from "../src/lib/demo-catalogue";
import { countItems, listItems } from "../src/lib/catalogue-store";
import { resetDbForTests } from "../src/lib/db";
import { generateTerminalKey } from "../src/lib/keys";
import { getHead, readAscending } from "../src/lib/journal-store";
import { recordSale } from "../src/lib/record-sale";
import { saleBodyDigest } from "../src/lib/sale";
import { summarise, summariseJournal } from "../src/lib/summary";
import { addToTab, openTab } from "../src/lib/tab-store";
import { saveIdentityOnce, getIdentity } from "../src/lib/terminal-store";
import { verifyLocalJournal } from "../src/lib/verify-journal";
import { toHex } from "../src/lib/bytes";

beforeEach(async () => {
  (globalThis as any).indexedDB = new IDBFactory();
  resetDbForTests();
  const k = await generateTerminalKey();
  await saveIdentityOnce({
    terminalId: "TERM-TEST0002", label: "Lane 1", publicKeySpkiBase64: k.publicKeySpkiBase64,
    privateKey: k.privateKey, publicKey: k.publicKey, enrolledAt: Date.now()
  });
});

describe("demo catalogue", () => {
  it("loads once and is idempotent", async () => {
    const first = await loadDemoCatalogue();
    expect(first).toEqual({ added: DEMO_ITEMS.length, skipped: 0 });
    expect(await loadDemoCatalogue()).toEqual({ added: 0, skipped: DEMO_ITEMS.length });
    expect(await countItems()).toBe(DEMO_ITEMS.length);
  });
  it("has unique SKUs, whole-shilling prices, and only 0% or 16% rates", () => {
    expect(new Set(DEMO_ITEMS.map((i) => i.sku)).size).toBe(DEMO_ITEMS.length);
    for (const i of DEMO_ITEMS) {
      expect(Number.isInteger(i.kes) && i.kes > 0).toBe(true);
      expect([0, 1600]).toContain(i.taxBp);
    }
  });
});

async function sell(name: string, cashier?: { staffId: string; staffNumber: string; name: string }) {
  const items = await listItems({ q: name, limit: 5 });
  const tab = await openTab("t");
  await addToTab(tab.id, items.items[0]);
  return recordSale({ tabId: tab.id, payments: [{ method: "CASH", tenderedMinor: 100000n, reference: "" }], cashier });
}

describe("attribution and summary", () => {
  it("a cashier-attributed sale is v2, commits to the cashier, and verifies", async () => {
    await loadDemoCatalogue();
    const who = { staffId: "STF-1", staffNumber: "2001", name: "Wanjiru" };
    const rec = await sell("sugar", who);
    expect(rec.sale.version).toBe("mara.sale.v2");
    expect(rec.sale.cashier).toEqual(who);
    const d1 = toHex(await saleBodyDigest(rec.sale));
    const tampered = structuredClone(rec.sale);
    tampered.cashier!.name = "Someone else";
    expect(toHex(await saleBodyDigest(tampered))).not.toBe(d1);
    const id = (await getIdentity())!;
    const report = await verifyLocalJournal({
      terminalId: id.terminalId, publicKeySpkiBase64: id.publicKeySpkiBase64,
      source: { head: getHead, page: readAscending }, pageSize: 4
    });
    expect(report.gaps).toEqual([]);
    expect(report.breaks).toEqual([]);
    expect(report.entriesChecked).toBe(1);
  });

  it("an unattributed sale stays v1 so old journals keep verifying", async () => {
    await loadDemoCatalogue();
    expect((await sell("bread")).sale.version).toBe("mara.sale.v1");
  });

  it("summarises per day, per tender, per item and per cashier from the journal", async () => {
    await loadDemoCatalogue();
    await sell("sugar", { staffId: "A", staffNumber: "1", name: "Amina" });
    await sell("sugar", { staffId: "A", staffNumber: "1", name: "Amina" });
    await sell("bread");
    const sum = await summariseJournal(2);
    expect(sum.entries).toBe(3);
    expect(sum.days).toHaveLength(1);
    const day = sum.days[0];
    expect(day.sales).toBe(3);
    // sugar 160 + 16% = 185.60 (x2), bread 65 exempt
    expect(day.grossMinor).toBe(18560n * 2n + 6500n);
    expect(day.taxMinor).toBe(2560n * 2n);
    expect(day.netMinor + day.taxMinor).toBe(day.grossMinor);
    expect(day.cashMinor).toBe(day.grossMinor);
    expect(day.mobileMinor).toBe(0n);
    expect(day.pending).toBe(3);
    expect(sum.items[0].name).toBe("Sugar 1kg");
    expect(sum.items[0].qty).toBe(2);
    expect(sum.cashiers.map((c) => c.name).sort()).toEqual(["(not signed in)", "Amina"]);
    expect(summarise([]).days).toEqual([]);
  });
});
