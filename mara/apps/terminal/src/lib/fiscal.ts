/**
 * Port of com.mara.platform.fiscal.FiscalLease.
 *
 * A lease is a contiguous block of fiscal invoice numbers issued to this terminal by the
 * server. NOTHING IN THIS APP CAN CREATE ONE: the service that would issue leases
 * (sync-service / fiscal) is not built, so the terminal holds no lease and every sale is
 * recorded FISCAL_PENDING. The type and the draw logic exist so that the moment a lease
 * can be installed, the sale path already numbers from it correctly and atomically.
 *
 * Numbers are BigInt; stored records carry them as decimal strings.
 */

export const RENEWAL_THRESHOLD = 0.2;

export interface FiscalLease {
  terminalId: string;
  firstNumber: bigint;
  lastNumber: bigint;
  nextNumber: bigint;
  issuedAtMs: number;
  expiresAtMs: number;
}

export function validateLease(l: FiscalLease): FiscalLease {
  if (l.terminalId.trim() === "") throw new Error("terminalId must not be blank");
  if (l.firstNumber < 1n) throw new Error(`fiscal numbers start at 1, got ${l.firstNumber}`);
  if (l.lastNumber < l.firstNumber) throw new Error("lease runs backwards");
  if (l.nextNumber < l.firstNumber || l.nextNumber > l.lastNumber + 1n) {
    throw new Error(`nextNumber ${l.nextNumber} outside lease ${l.firstNumber}..${l.lastNumber}`);
  }
  if (l.expiresAtMs <= l.issuedAtMs) throw new Error("lease expires at or before it was issued");
  return l;
}

export const size = (l: FiscalLease): bigint => l.lastNumber - l.firstNumber + 1n;
export const remaining = (l: FiscalLease): bigint => l.lastNumber - l.nextNumber + 1n;
export const isExhausted = (l: FiscalLease): boolean => l.nextNumber > l.lastNumber;
export const isExpired = (l: FiscalLease, nowMs: number): boolean => nowMs >= l.expiresAtMs;

export function needsRenewal(l: FiscalLease, nowMs: number): boolean {
  return (
    isExhausted(l) ||
    isExpired(l, nowMs) ||
    Number(remaining(l)) / Number(size(l)) <= RENEWAL_THRESHOLD
  );
}

/** Takes the next number if one is left; empty (null) when exhausted, never throws. */
export function draw(l: FiscalLease): { number: bigint; remainder: FiscalLease } | null {
  if (isExhausted(l)) return null;
  return { number: l.nextNumber, remainder: { ...l, nextNumber: l.nextNumber + 1n } };
}

export function unusedRange(l: FiscalLease): [bigint, bigint] | null {
  return isExhausted(l) ? null : [l.nextNumber, l.lastNumber];
}

export type FiscalStatus = "NUMBERED" | "FISCAL_PENDING";

export interface FiscalDecision {
  status: FiscalStatus;
  number: bigint | null;
  /** why the sale is pending, for the receipt and the journal screen */
  reason: "NO_LEASE" | "LEASE_EXPIRED" | "LEASE_EXHAUSTED" | null;
  lease: FiscalLease | null;
}

/**
 * Terminal-side policy: a sale is NUMBERED only from a live lease with numbers left.
 * An expired lease is treated as unusable (the Java draw() does not look at expiry, but
 * needsRenewal() treats expiry as a reason to replace it, and issuing from a lease the
 * server considers dead risks a number it has already voided). Otherwise the sale
 * proceeds as FISCAL_PENDING; degradation is graded, never a refusal to sell.
 */
export function decideFiscal(lease: FiscalLease | null, nowMs: number): FiscalDecision {
  if (!lease) return { status: "FISCAL_PENDING", number: null, reason: "NO_LEASE", lease: null };
  if (isExpired(lease, nowMs)) {
    return { status: "FISCAL_PENDING", number: null, reason: "LEASE_EXPIRED", lease };
  }
  const drawn = draw(lease);
  if (!drawn) return { status: "FISCAL_PENDING", number: null, reason: "LEASE_EXHAUSTED", lease };
  return { status: "NUMBERED", number: drawn.number, reason: null, lease: drawn.remainder };
}
