/**
 * Produces, and checks, the fixture the Java server-side verifier is tested against.
 *
 * The direction of trust is the reverse of java-vectors.json: there the Java classes made the
 * vectors and the terminal had to match them; here the terminal's own code makes a real
 * enrolled journal (v1 and v2 bodies, awkward strings, a numbered and a pending sale) and the
 * server's Java must recompute exactly the digests and verify exactly the signatures this code
 * produced. Regenerate with `UPDATE_VECTORS=1 npx vitest run test/ts-journal-fixture.test.ts`,
 * then run the Java platform tests.
 */
import "fake-indexeddb/auto";
import { IDBFactory } from "fake-indexeddb";
import { readFileSync, writeFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { beforeEach, describe, expect, it } from "vitest";
import { createItem } from "../src/lib/catalogue-store";
import { resetDbForTests } from "../src/lib/db";
import { generateTerminalKey, verifyDigest } from "../src/lib/keys";
import { readAscending } from "../src/lib/journal-store";
import { recordSale } from "../src/lib/record-sale";
import { addToTab, openTab } from "../src/lib/tab-store";
import { saveIdentityOnce } from "../src/lib/terminal-store";
import { fromHex } from "../src/lib/bytes";
import { signedHeaders } from "../src/lib/sync";
import { getIdentity } from "../src/lib/terminal-store";

const FIXTURE = fileURLToPath(new URL("./fixtures/ts-journal.json", import.meta.url));
const TERMINAL = "TERM-0123456789ABCDEF0123";

beforeEach(() => {
  (globalThis as { indexedDB: IDBFactory }).indexedDB = new IDBFactory();
  resetDbForTests();
});

async function buildJournal() {
  const k = await generateTerminalKey();
  await saveIdentityOnce({
    terminalId: TERMINAL, label: "Lane 1", publicKeySpkiBase64: k.publicKeySpkiBase64,
    privateKey: k.privateKey, publicKey: k.publicKey, enrolledAt: 1_700_000_000_000
  });
  const sell = async (name: string, sku: string, unit: bigint, taxBp: number, qty: number, cashier?: boolean, at?: number) => {
    const item = await createItem({ sku, name, unitMinor: unit, taxBp });
    const tab = await openTab("t");
    for (let i = 0; i < qty; i++) await addToTab(tab.id, item);
    return recordSale({
      tabId: tab.id,
      payments: [{ method: "CASH", tenderedMinor: unit * BigInt(qty) * 2n, reference: "" }],
      nowMs: at,
      cashier: cashier ? { staffId: "STAFF-1", staffNumber: "2001", name: "Wanjiru \"W\" Kamau" } : undefined
    });
  };
  const t0 = 1_760_000_000_000;
  await sell("Unga wa Ngano 2kg", "UNGA-2", 21500n, 0, 2, false, t0);
  await sell("Maziwa \"Fresh\" 500ml \\ tab\there", "MZ-5", 7000n, 1600, 3, true, t0 + 1500);
  await sell("Chai ya Tangawizi ☕ éè 日本", "CH-1", 12999n, 1600, 1, true, t0 + 2750);
  await sell("Bread\nSliced \u0001", "BR-1", 6500n, 1600, 5, true, t0 + 9000);
  const entries = await readAscending(0, 100);
  // A request the terminal signed the way it signs uploads, for the server's RequestSignature.
  const identity = (await getIdentity())!;
  const requestBody = JSON.stringify({ entries: entries.slice(0, 1) });
  const path = "/v1/terminal/sync/journal";
  const h = await signedHeaders(identity, "POST", path, requestBody, 1_760_000_100_000);
  const request = {
    method: "POST", path, body: requestBody, epochSecond: Number(h["x-mara-timestamp"]), signature: h["x-mara-signature"]
  };
  return { publicKeySpkiBase64: k.publicKeySpkiBase64, terminalId: TERMINAL, entries, request };
}

describe("ts journal fixture", () => {
  it("is current, and every signature in it verifies under the terminal's own verifier", async () => {
    const built = await buildJournal();
    if (process.env.UPDATE_VECTORS) {
      writeFileSync(FIXTURE, JSON.stringify(built, null, 2) + "\n");
    }
    const onDisk = JSON.parse(readFileSync(FIXTURE, "utf8"));
    expect(onDisk.entries.length).toBe(4);
    for (const e of onDisk.entries) {
      expect(await verifyDigest(onDisk.publicKeySpkiBase64, fromHex(e.digest), e.signature)).toBe(true);
    }
    // Ed25519 signatures are deterministic but the key is random per run, so the fixture can
    // only be compared structurally: same sales, same shape.
    expect(onDisk.entries.map((e: { sale: { lines: { sku: string }[] } }) => e.sale.lines[0].sku)).toEqual(
      built.entries.map((e) => e.sale.lines[0].sku)
    );
  });
});
