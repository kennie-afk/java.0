/**
 * Sale-path load generator: many real terminals, each with its own Ed25519 key and hash chain, selling and
 * uploading to a Mara stack exactly as the till does, using the till's own libraries (pricing, canonical sale,
 * chain digest, request signature), so every entry is one the servers genuinely verify.
 *
 *   LOAD_OPERATOR_CREDENTIAL=mop_... npx vite-node scripts/loadtest.ts -- \
 *       --identity http://localhost:8081 --sync http://localhost:8082 --core http://localhost:8083 \
 *       --tenants 20 --terminals 10 --rate 150 --duration 60 --out /tmp/load.json
 *
 * Open loop: sales arrive at --rate per second in total (exponential gaps per terminal), whatever the
 * server is doing. A terminal uploads everything after the server-confirmed cursor, one request at a time,
 * and resends the same entries after a failure (that is what the till does), so a stalled server shows as
 * growing sale-to-sync latency and a backlog, not as a throttled test. The summary is written to --out; the
 * companion scripts/loadtest/verify.py reads the databases and checks nothing was lost or duplicated.
 */
import { writeFileSync } from "node:fs";
import { toHex } from "../src/lib/bytes";
import { type ChainEntry, chainDigest, GENESIS, instantFromMillis, sha256 } from "../src/lib/chain";
import { generateTerminalKey, signDigest } from "../src/lib/keys";
import { applyTender, buildSaleBody, priceCart, saleBodyDigest } from "../src/lib/sale";
import { requestMessage } from "../src/lib/sync";
import { utf8 } from "../src/lib/bytes";

const argv = process.argv.slice(2).filter((a) => a !== "--");
const arg = (name: string, def: string) => {
  const i = argv.indexOf(`--${name}`);
  return i >= 0 ? argv[i + 1] : def;
};
const IDENTITY = arg("identity", "http://localhost:8081");
const SYNC = arg("sync", "http://localhost:8082");
const TENANTS = Number(arg("tenants", "20"));
const PER_TENANT = Number(arg("terminals", "10"));
const RATE = Number(arg("rate", "100"));
const DURATION = Number(arg("duration", "60"));
const BATCH = Number(arg("batch", "20"));
const OUT = arg("out", "/tmp/mara-load.json");
const CRED = process.env.LOAD_OPERATOR_CREDENTIAL;
if (!CRED) throw new Error("set LOAD_OPERATOR_CREDENTIAL to a platform operator credential (see scripts/loadtest/run.sh)");

const json = { "content-type": "application/json" };
async function admin(method: string, path: string, tenant: string | null, body?: unknown) {
  const res = await fetch(IDENTITY + path, {
    method,
    headers: { ...json, authorization: `Bearer ${CRED}`, ...(tenant ? { "x-mara-tenant": tenant } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body)
  });
  const text = await res.text();
  if (res.status >= 300) throw new Error(`${method} ${path} -> ${res.status} ${text}`);
  return text ? JSON.parse(text) : null;
}

interface Terminal {
  id: string;
  tenant: string;
  key: CryptoKey;
  seq: number;
  headDigest: Uint8Array;
  lastMs: number;
  pending: { record: Record<string, unknown>; createdMs: number }[];
  inFlight: boolean;
  confirmed: number;
  totalMinor: bigint;
}

// ----------------------------------------------------------------------------- setup
async function setup(): Promise<Terminal[]> {
  const terminals: Terminal[] = [];
  const stamp = Date.now().toString(36);
  const work: (() => Promise<void>)[] = [];
  for (let t = 0; t < TENANTS; t++) {
    work.push(async () => {
      const created = await admin("POST", "/v1/admin/tenants", null, {
        legalName: `Load ${stamp}-${t} Ltd`, tradingName: `Load ${stamp}-${t}`, countryCode: "KE", currency: "KES",
        licensedTerminals: Math.max(PER_TENANT, 1), branchName: "Main", timezone: "Africa/Nairobi",
        ownerName: "Owner", ownerStaffNumber: "1", ownerPin: "483920"
      });
      for (let k = 0; k < PER_TENANT; k++) {
        const code = await admin("POST", "/v1/admin/enrolment-codes", created.tenantId,
          { branchId: created.branchId, issuedBy: created.ownerStaffId });
        const key = await generateTerminalKey();
        const res = await fetch(IDENTITY + "/v1/enrolment", {
          method: "POST", headers: json,
          body: JSON.stringify({ code: code.code, publicKey: key.publicKeySpkiBase64, label: `Lane ${k + 1}` })
        });
        if (res.status !== 201) throw new Error(`enrol -> ${res.status} ${await res.text()}`);
        const { terminalId } = (await res.json()) as { terminalId: string };
        terminals.push({ id: terminalId, tenant: created.tenantId, key: key.privateKey, seq: 0, headDigest: GENESIS,
          lastMs: 0, pending: [], inFlight: false, confirmed: 0, totalMinor: 0n });
      }
    });
  }
  // a few tenants at a time: provisioning is not what is being measured
  const lanes = 4;
  await Promise.all(Array.from({ length: lanes }, async (_, lane) => {
    for (let i = lane; i < work.length; i += lanes) await work[i]();
  }));
  return terminals;
}

// ----------------------------------------------------------------------------- selling
const SKUS = [["SUGAR-2KG", "Sugar 2kg", 28000n], ["MILK-500", "Milk 500ml", 6500n], ["BREAD", "Bread", 6000n], ["RICE-1KG", "Rice 1kg", 19000n]] as const;

async function sell(t: Terminal, nowMs: number): Promise<void> {
  const [sku, name, unit] = SKUS[Math.floor(Math.random() * SKUS.length)];
  const qty = 1 + Math.floor(Math.random() * 3);
  const totals = priceCart("KES", [{ sku, name, unitMinor: unit, taxBp: 1600, qty }]);
  const tender = applyTender(totals.total, [{ method: "CASH", tenderedMinor: totals.total.minor, reference: "" }]);
  if (!tender.ok) throw new Error(tender.error);
  const body = buildSaleBody("KES", totals, tender.applied, { status: "FISCAL_PENDING", number: null });
  const ms = Math.max(nowMs, t.lastMs);
  const { epochSecond, nano } = instantFromMillis(ms);
  const bodyDigest = await saleBodyDigest(body);
  t.seq += 1;
  const entry: ChainEntry = { terminalId: t.id, sequence: BigInt(t.seq), epochSecond, nano, bodyDigest, previousDigest: t.headDigest };
  const digest = await chainDigest(entry);
  const signature = await signDigest(t.key, digest);
  t.pending.push({
    createdMs: Date.now(),
    record: { sequence: t.seq, terminalId: t.id, epochSecond: Number(epochSecond), nano, sale: body, bodyDigest: toHex(bodyDigest),
      previousDigest: toHex(t.headDigest), digest: toHex(digest), signature }
  });
  t.headDigest = digest;
  t.lastMs = ms;
  t.totalMinor += totals.total.minor;
}

// ----------------------------------------------------------------------------- uploading
const uploadLatency: number[] = [];
const syncLatency: number[] = [];
const statuses = new Map<string, number>();
let retries = 0;
const bump = (k: string) => statuses.set(k, (statuses.get(k) ?? 0) + 1);

async function signedPost(t: Terminal, path: string, body: string): Promise<Response> {
  const epoch = Math.floor(Date.now() / 1000);
  const hash = toHex(await sha256(utf8(body)));
  const signature = await signDigest(t.key, requestMessage(t.id, epoch, "POST", path, hash));
  return fetch(SYNC + path, {
    method: "POST", body,
    headers: { ...json, "x-mara-terminal": t.id, "x-mara-timestamp": String(epoch), "x-mara-signature": signature },
    signal: AbortSignal.timeout(30_000)
  });
}

async function pump(t: Terminal): Promise<void> {
  if (t.inFlight || t.pending.length === 0) return;
  t.inFlight = true;
  try {
    while (t.pending.length > 0) {
      const batch = t.pending.slice(0, BATCH);
      const started = performance.now();
      let status = "error";
      try {
        const res = await signedPost(t, "/v1/terminal/sync/journal", JSON.stringify({ entries: batch.map((b) => b.record) }));
        status = String(res.status);
        uploadLatency.push(performance.now() - started);
        if (res.status === 200) {
          const out = (await res.json()) as { acceptedThrough: number; gap: unknown; refused?: unknown[] };
          const through = Math.min(Number(out.acceptedThrough), batch[batch.length - 1].record.sequence as number);
          const done = t.pending.filter((p) => (p.record.sequence as number) <= through);
          const now = Date.now();
          for (const d of done) syncLatency.push(now - d.createdMs);
          t.pending = t.pending.filter((p) => (p.record.sequence as number) > through);
          t.confirmed = Math.max(t.confirmed, through);
          bump("200");
          if (out.gap || (out.refused?.length ?? 0) > 0) bump("200-with-gap-or-refusal");
          continue;
        }
      } catch {
        bump("network-error");
      }
      if (status !== "200") bump(status);
      retries++;
      await new Promise((r) => setTimeout(r, 300 + Math.random() * 400));   // resend the same entries
    }
  } finally {
    t.inFlight = false;
  }
}

const pct = (xs: number[], p: number) => {
  if (xs.length === 0) return 0;
  const s = [...xs].sort((a, b) => a - b);
  return s[Math.min(s.length - 1, Math.floor((p / 100) * s.length))];
};

// ----------------------------------------------------------------------------- run
(async () => {
  console.log(`setting up ${TENANTS} tenants x ${PER_TENANT} terminals ...`);
  const t0 = performance.now();
  const terminals = await setup();
  console.log(`  ${terminals.length} terminals enrolled in ${((performance.now() - t0) / 1000).toFixed(1)} s`);

  const perTerminalRate = RATE / terminals.length;
  const endAt = Date.now() + DURATION * 1000;
  let sold = 0;
  console.log(`selling at ${RATE}/s total (${perTerminalRate.toFixed(3)}/s per terminal) for ${DURATION} s ...`);
  const started = Date.now();
  await Promise.all(terminals.map(async (t) => {
    // desynchronise the tills, then exponential gaps
    await new Promise((r) => setTimeout(r, Math.random() * (1000 / perTerminalRate)));
    while (Date.now() < endAt) {
      await sell(t, Date.now());
      sold++;
      void pump(t);
      await new Promise((r) => setTimeout(r, -Math.log(1 - Math.random()) * (1000 / perTerminalRate)));
    }
  }));
  const sellSeconds = (Date.now() - started) / 1000;
  console.log(`  generated ${sold} sales in ${sellSeconds.toFixed(1)} s; draining uploads ...`);
  const drainStart = Date.now();
  while (terminals.some((t) => t.pending.length > 0 || t.inFlight) && Date.now() - drainStart < 120_000) {
    terminals.forEach((t) => void pump(t));
    await new Promise((r) => setTimeout(r, 200));
  }
  const backlog = terminals.reduce((n, t) => n + t.pending.length, 0);

  const summary = {
    when: new Date().toISOString(),
    config: { tenants: TENANTS, terminalsPerTenant: PER_TENANT, targetRatePerSecond: RATE, durationSeconds: DURATION, batch: BATCH },
    generated: sold,
    achievedSalesPerSecond: Number((sold / sellSeconds).toFixed(1)),
    drainedSeconds: Number(((Date.now() - drainStart) / 1000).toFixed(1)),
    unconfirmedAtEnd: backlog,
    uploadRequests: { count: uploadLatency.length, p50ms: Math.round(pct(uploadLatency, 50)), p95ms: Math.round(pct(uploadLatency, 95)), p99ms: Math.round(pct(uploadLatency, 99)) },
    saleToServerConfirmedMs: { p50: Math.round(pct(syncLatency, 50)), p95: Math.round(pct(syncLatency, 95)), p99: Math.round(pct(syncLatency, 99)), max: Math.round(Math.max(0, ...syncLatency)) },
    responses: Object.fromEntries(statuses),
    resends: retries,
    terminals: terminals.map((t) => ({ id: t.id, tenant: t.tenant, sales: t.seq, confirmed: t.confirmed, headDigest: toHex(t.headDigest), totalMinor: t.totalMinor.toString() }))
  };
  writeFileSync(OUT, JSON.stringify(summary, null, 1));
  const { terminals: _t, ...print } = summary;
  console.log(JSON.stringify(print, null, 1));
})().catch((e) => { console.error(e); process.exit(1); });
