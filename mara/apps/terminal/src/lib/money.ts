/**
 * Port of com.mara.platform.money.Money.
 *
 * Amounts are BigInt minor units and never a JS number: a double cannot hold an int64,
 * and 0.1 + 0.2 on a receipt is a tax record that does not reconcile. The Java class is
 * backed by a `long` and uses Math.*Exact, so it throws instead of wrapping; this port
 * keeps that contract by refusing any result outside the signed 64-bit range.
 *
 * Correctness against the Java implementation is asserted in test/money.test.ts using
 * vectors produced by running the Java class itself (vectors/Vectors.java).
 */

const INT64_MAX = (1n << 63n) - 1n;
const INT64_MIN = -(1n << 63n);

export class MoneyOverflowError extends Error {
  constructor(operation: string) {
    super(`money overflow in ${operation}`);
    this.name = "MoneyOverflowError";
  }
}

export class CurrencyMismatchError extends Error {
  constructor(a: string, b: string) {
    super(`currency mismatch: ${a} vs ${b}`);
    this.name = "CurrencyMismatchError";
  }
}

/** ISO-4217 minor-unit digits, matching java.util.Currency#getDefaultFractionDigits. */
export const CURRENCY_DIGITS: Readonly<Record<string, number>> = {
  KES: 2,
  USD: 2,
  EUR: 2,
  GBP: 2,
  TZS: 2,
  UGX: 0,
  RWF: 0,
  JPY: 0,
  TND: 3
};

export interface Money {
  readonly minor: bigint;
  readonly currency: string;
}

function checked(value: bigint, operation: string): bigint {
  if (value > INT64_MAX || value < INT64_MIN) {
    throw new MoneyOverflowError(operation);
  }
  return value;
}

export function digitsFor(currency: string): number {
  const digits = CURRENCY_DIGITS[currency];
  if (digits === undefined) {
    throw new Error(`unsupported currency ${currency}`);
  }
  return digits;
}

export function money(minor: bigint, currency: string): Money {
  digitsFor(currency);
  return { minor: checked(minor, "of"), currency };
}

export function zero(currency: string): Money {
  return money(0n, currency);
}

function sameCurrency(a: Money, b: Money): void {
  if (a.currency !== b.currency) {
    throw new CurrencyMismatchError(a.currency, b.currency);
  }
}

export function plus(a: Money, b: Money): Money {
  sameCurrency(a, b);
  return { minor: checked(a.minor + b.minor, "plus"), currency: a.currency };
}

export function minus(a: Money, b: Money): Money {
  sameCurrency(a, b);
  return { minor: checked(a.minor - b.minor, "minus"), currency: a.currency };
}

export function negated(a: Money): Money {
  return { minor: checked(-a.minor, "negated"), currency: a.currency };
}

export function times(a: Money, factor: bigint): Money {
  return { minor: checked(a.minor * factor, "times"), currency: a.currency };
}

export function sum(currency: string, amounts: Iterable<Money>): Money {
  let total = zero(currency);
  for (const amount of amounts) {
    total = plus(total, amount);
  }
  return total;
}

/**
 * Splits an amount into `parts` pieces that sum back to exactly the amount. The
 * remainder goes one minor unit at a time to the leading parts (100 in 3 is 34, 33, 33).
 * BigInt division truncates toward zero exactly as Java's long division does, so the
 * sign handling for negative amounts is the same as the original.
 */
export function allocate(a: Money, parts: number): Money[] {
  if (!Number.isInteger(parts) || parts < 1) {
    throw new RangeError(`cannot allocate into ${parts} parts`);
  }
  const n = BigInt(parts);
  const base = a.minor / n;
  const remainder = a.minor - base * n;
  const step = remainder < 0n ? -1n : 1n;
  const absRemainder = remainder < 0n ? -remainder : remainder;

  const out: Money[] = [];
  for (let i = 0; i < parts; i++) {
    const extra = absRemainder > BigInt(i) ? step : 0n;
    out.push({ minor: base + extra, currency: a.currency });
  }
  return out;
}

/**
 * Applies a rate in basis points (1600 is 16%), half-up on the exact integer product,
 * away from zero on a tie and symmetric for negatives. Like the Java, the product is
 * computed exactly before dividing and overflows rather than wraps.
 */
export function percentage(a: Money, basisPoints: bigint): Money {
  if (basisPoints < 0n) {
    throw new RangeError(`basis points must not be negative: ${basisPoints}`);
  }
  const numerator = checked(a.minor * basisPoints, "percentage");
  return { minor: roundHalfUp(numerator, 10_000n), currency: a.currency };
}

function roundHalfUp(numerator: bigint, denominator: bigint): bigint {
  let quotient = numerator / denominator;
  const remainder = numerator % denominator;
  const absRemainder = remainder < 0n ? -remainder : remainder;
  if (absRemainder * 2n >= denominator) {
    quotient += numerator < 0n ? -1n : 1n;
  }
  return quotient;
}

/** "KES 1234.56". For humans and receipts only; never parsed back or used in arithmetic. */
export function format(a: Money): string {
  const digits = digitsFor(a.currency);
  if (digits === 0) {
    return `${a.currency} ${a.minor}`;
  }
  const scale = 10n ** BigInt(digits);
  const units = a.minor / scale;
  const rawFraction = a.minor % scale;
  const fraction = rawFraction < 0n ? -rawFraction : rawFraction;
  const sign = a.minor < 0n && units === 0n ? "-" : "";
  return `${a.currency} ${sign}${units}.${fraction.toString().padStart(digits, "0")}`;
}

/**
 * Parses what a cashier types ("1234.5", "1,234.50") into minor units, exactly, without
 * ever passing through a float. Returns null for anything ambiguous rather than guessing.
 */
export function parseMajor(input: string, currency: string): bigint | null {
  const digits = digitsFor(currency);
  const cleaned = input.trim().replace(/,/g, "");
  const match = /^(\d+)(?:\.(\d*))?$/.exec(cleaned);
  if (!match) return null;
  const fraction = match[2] ?? "";
  if (fraction.length > digits) return null;
  const minor = BigInt(match[1] + fraction.padEnd(digits, "0"));
  return minor > INT64_MAX ? null : minor;
}
