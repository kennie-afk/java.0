/**
 * Cart arithmetic and the canonical sale body.
 *
 * Prices are TAX-EXCLUSIVE: line net = unit price x quantity, line tax = Money.percentage
 * (the ported, Java-verified half-up basis-point rounding) of that net, and the total is
 * net + tax. Rounding happens per line and only there, so the receipt's lines always add
 * up to its total.
 */
import { bodyDigest, longBytes } from "./chain";
import { utf8 } from "./bytes";
import {
  allocate,
  type Money,
  money,
  percentage,
  plus,
  sum,
  times,
  zero
} from "./money";

export interface CartLine {
  sku: string;
  name: string;
  unitMinor: bigint;
  taxBp: number;
  qty: number;
}

export interface PricedLine extends CartLine {
  netMinor: bigint;
  taxMinor: bigint;
}

export interface Totals {
  lines: PricedLine[];
  net: Money;
  tax: Money;
  total: Money;
}

export function priceCart(currency: string, lines: CartLine[]): Totals {
  const priced: PricedLine[] = lines.map((l) => {
    if (!Number.isInteger(l.qty) || l.qty < 1) throw new RangeError(`bad quantity for ${l.sku}`);
    if (!Number.isInteger(l.taxBp) || l.taxBp < 0) throw new RangeError(`bad tax rate for ${l.sku}`);
    const net = times(money(l.unitMinor, currency), BigInt(l.qty));
    const tax = percentage(net, BigInt(l.taxBp));
    return { ...l, netMinor: net.minor, taxMinor: tax.minor };
  });
  const net = sum(currency, priced.map((l) => money(l.netMinor, currency)));
  const tax = sum(currency, priced.map((l) => money(l.taxMinor, currency)));
  return { lines: priced, net, tax, total: plus(net, tax) };
}

/** Splits a total into `people` shares that always sum back to it. */
export function splitEvenly(total: Money, people: number): Money[] {
  return allocate(total, people);
}

export type PayMethod = "CASH" | "MOBILE_MONEY";

export interface PaymentInput {
  method: PayMethod;
  /** what the customer handed over, minor units */
  tenderedMinor: bigint;
  /** free-text reference the cashier typed; NOT verified against any provider */
  reference: string;
}

export interface AppliedPayment {
  method: PayMethod;
  /** the part of the tender that settled the sale */
  appliedMinor: bigint;
  tenderedMinor: bigint;
  reference: string;
}

export type TenderResult =
  | { ok: true; applied: AppliedPayment[]; changeMinor: bigint }
  | { ok: false; error: string };

/**
 * Applies tenders to a total. Invariants, all checked here rather than trusted to the UI:
 *   - the applied amounts sum to the total exactly (no cent lost or invented);
 *   - only cash can produce change, and never more change than the cash handed over;
 *   - a sale is never recorded part-paid.
 */
export function applyTender(total: Money, payments: PaymentInput[]): TenderResult {
  if (total.minor <= 0n) return { ok: false, error: "The total must be greater than zero." };
  if (payments.length === 0) return { ok: false, error: "Add at least one payment." };
  let tendered = 0n;
  let cash = 0n;
  for (const p of payments) {
    if (p.tenderedMinor <= 0n) return { ok: false, error: "Every payment must be greater than zero." };
    tendered += p.tenderedMinor;
    if (p.method === "CASH") cash += p.tenderedMinor;
  }
  if (tendered < total.minor) {
    return { ok: false, error: "The payments do not cover the total." };
  }
  const change = tendered - total.minor;
  if (change > cash) {
    return { ok: false, error: "Only cash can be given back as change; the non-cash payments exceed the total." };
  }
  // Change comes out of cash, from the last cash payment backwards.
  let toTake = change;
  const applied: AppliedPayment[] = payments.map((p) => ({
    method: p.method,
    appliedMinor: p.tenderedMinor,
    tenderedMinor: p.tenderedMinor,
    reference: p.reference.trim()
  }));
  for (let i = applied.length - 1; i >= 0 && toTake > 0n; i--) {
    if (applied[i].method !== "CASH") continue;
    const take = toTake < applied[i].appliedMinor ? toTake : applied[i].appliedMinor;
    applied[i].appliedMinor -= take;
    toTake -= take;
  }
  const appliedSum = applied.reduce((n, p) => n + p.appliedMinor, 0n);
  if (toTake !== 0n || appliedSum !== total.minor) {
    return { ok: false, error: "Internal consistency check failed; nothing was recorded." };
  }
  // A cash payment fully absorbed by change would be a zero-amount line; drop it.
  return { ok: true, applied: applied.filter((p) => p.appliedMinor > 0n), changeMinor: change };
}

// ---------------------------------------------------------------------- body

/** Everything that is committed to by the chain. Nothing else about a sale is signed. */
export interface SaleCashier {
  staffId: string;
  staffNumber: string;
  name: string;
}

export interface SaleBody {
  /** v2 adds the signed-in cashier to what the chain commits to; v1 bodies stay valid forever. */
  version: "mara.sale.v1" | "mara.sale.v2";
  currency: string;
  lines: {
    sku: string;
    name: string;
    qty: number;
    unitMinor: string;
    taxBp: number;
    netMinor: string;
    taxMinor: string;
  }[];
  payments: {
    method: PayMethod;
    appliedMinor: string;
    reference: string;
    tenderedMinor: string;
  }[];
  totalMinor: string;
  fiscal: { status: "NUMBERED" | "FISCAL_PENDING"; number: string };
  /** Present exactly when version is v2. */
  cashier?: SaleCashier;
}

export function canonicalCashier(body: Pick<SaleBody, "cashier">): string {
  const c = body.cashier;
  return c ? JSON.stringify([c.staffId, c.staffNumber, c.name]) : "";
}

export function canonicalLines(body: Pick<SaleBody, "lines">): string {
  return JSON.stringify(
    body.lines.map((l) => [l.sku, l.name, String(l.qty), l.unitMinor, String(l.taxBp), l.netMinor, l.taxMinor])
  );
}

export function canonicalPayments(body: Pick<SaleBody, "payments">): string {
  return JSON.stringify(body.payments.map((p) => [p.method, p.appliedMinor, p.reference, p.tenderedMinor]));
}

export function canonicalFiscal(body: Pick<SaleBody, "fiscal">): string {
  return `${body.fiscal.status}:${body.fiscal.number}`;
}

/**
 * Digest of the sale body. The platform's ChainDigest.body() takes caller-canonicalised
 * fields and does not itself define a sale encoding ("this type deliberately does not
 * know how a sale is shaped"), so the field list below is this terminal's own contract:
 * version, currency, lines, payments, total (int64), fiscal.
 */
export async function saleBodyDigest(body: SaleBody): Promise<Uint8Array> {
  return bodyDigestFromFields(
    body.version,
    body.currency,
    canonicalLines(body),
    canonicalPayments(body),
    BigInt(body.totalMinor),
    canonicalFiscal(body),
    body.version === "mara.sale.v2" ? canonicalCashier(body) : undefined
  );
}

export function bodyDigestFromFields(
  version: string,
  currency: string,
  lines: string,
  payments: string,
  totalMinor: bigint,
  fiscal: string,
  cashier?: string
): Promise<Uint8Array> {
  const fields = [utf8(version), utf8(currency), utf8(lines), utf8(payments), longBytes(totalMinor), utf8(fiscal)];
  // The seventh field exists only in v2, so every v1 digest (and the Java parity vectors
  // that pin them) is byte-for-byte unchanged.
  if (cashier !== undefined) fields.push(utf8(cashier));
  return bodyDigest(...fields);
}

export function buildSaleBody(
  currency: string,
  totals: Totals,
  applied: AppliedPayment[],
  fiscal: { status: "NUMBERED" | "FISCAL_PENDING"; number: bigint | null },
  cashier?: SaleCashier
): SaleBody {
  return {
    version: cashier ? "mara.sale.v2" : "mara.sale.v1",
    currency,
    lines: totals.lines.map((l) => ({
      sku: l.sku,
      name: l.name,
      qty: l.qty,
      unitMinor: l.unitMinor.toString(),
      taxBp: l.taxBp,
      netMinor: l.netMinor.toString(),
      taxMinor: l.taxMinor.toString()
    })),
    payments: applied.map((p) => ({
      method: p.method,
      appliedMinor: p.appliedMinor.toString(),
      reference: p.reference,
      tenderedMinor: p.tenderedMinor.toString()
    })),
    totalMinor: totals.total.minor.toString(),
    fiscal: { status: fiscal.status, number: fiscal.number === null ? "" : fiscal.number.toString() },
    ...(cashier ? { cashier: { staffId: cashier.staffId, staffNumber: cashier.staffNumber, name: cashier.name } } : {})
  };
}

export { zero };
