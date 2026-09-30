/**
 * Port of com.mara.platform.journal.ChainDigest and JournalEntry.
 *
 * The encoding is byte-for-byte the Java one:
 *
 *   digest = SHA-256( previousDigest(32)
 *                     || u32be(len) || utf8(terminalId)
 *                     || i64be(sequence)
 *                     || i64be(epochSecond)
 *                     || i64be(nano)          <- 8 bytes, as Java's longBytes(getNano())
 *                     || u32be(len) || bodyDigest )
 *
 * Every variable-length field carries a length prefix, so bytes cannot be shifted across a
 * field boundary to produce two entries with one digest. Equality with the Java output is
 * asserted against vectors the Java code produced (test/chain.test.ts).
 */
import { bytesEqual, concat, fromHex, toHex, utf8 } from "./bytes";

export const GENESIS: Uint8Array = new Uint8Array(32);

export async function sha256(data: Uint8Array): Promise<Uint8Array> {
  const view = new Uint8Array(data);   // detach from any shared buffer type quirks
  return new Uint8Array(await crypto.subtle.digest("SHA-256", view));
}

export function longBytes(value: bigint): Uint8Array {
  const out = new Uint8Array(8);
  new DataView(out.buffer).setBigInt64(0, value, false);
  return out;
}

function intBytes(value: number): Uint8Array {
  const out = new Uint8Array(4);
  new DataView(out.buffer).setInt32(0, value, false);
  return out;
}

export function lengthPrefixed(value: Uint8Array): Uint8Array {
  return concat(intBytes(value.length), value);
}

/** Digest of a sale body from its already-canonical fields, each length-prefixed. */
export async function bodyDigest(...fields: Uint8Array[]): Promise<Uint8Array> {
  return sha256(concat(...fields.map(lengthPrefixed)));
}

export interface ChainEntry {
  terminalId: string;
  /** starts at 1, strictly monotonic per terminal, never reused */
  sequence: bigint;
  epochSecond: bigint;
  /** nanosecond-of-second, 0..999_999_999 */
  nano: number;
  bodyDigest: Uint8Array;
  previousDigest: Uint8Array;
}

/** Same invariants the Java record enforces in its compact constructor. */
export function assertValidEntry(entry: ChainEntry): void {
  if (entry.terminalId.trim() === "") {
    throw new Error("terminalId must not be blank");
  }
  if (entry.sequence < 1n) {
    throw new Error(`sequence starts at 1, got ${entry.sequence}`);
  }
}

export async function chainDigest(entry: ChainEntry): Promise<Uint8Array> {
  assertValidEntry(entry);
  return sha256(
    concat(
      entry.previousDigest,
      lengthPrefixed(utf8(entry.terminalId)),
      longBytes(entry.sequence),
      longBytes(entry.epochSecond),
      longBytes(BigInt(entry.nano)),
      lengthPrefixed(entry.bodyDigest)
    )
  );
}

export function isGenesis(entry: ChainEntry): boolean {
  return entry.sequence === 1n;
}

export function digestsEqual(a: Uint8Array, b: Uint8Array): boolean {
  return bytesEqual(a, b);
}

export function hex(bytes: Uint8Array): string {
  return toHex(bytes);
}

export { fromHex };

/** Splits a millisecond wall-clock reading into the (second, nano) pair the chain signs. */
export function instantFromMillis(ms: number): { epochSecond: bigint; nano: number } {
  const whole = Math.floor(ms / 1000);
  return { epochSecond: BigInt(whole), nano: (ms - whole * 1000) * 1_000_000 };
}
