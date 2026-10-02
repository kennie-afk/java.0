import type { SaleBody } from "./sale";

export interface TerminalIdentity {
  terminalId: string;
  label: string;
  publicKeySpkiBase64: string;
  /** Ed25519 private key, generated non-extractable: this code cannot read its bytes. */
  privateKey: CryptoKey;
  publicKey: CryptoKey;
  enrolledAt: number;
}

export interface Settings {
  currency: string;
  shopName: string;
}

export interface CatalogueItem {
  id: string;
  sku: string;
  skuKey: string;
  name: string;
  nameKey: string;
  unitMinor: string;
  taxBp: number;
  createdAt: number;
}

export interface TabLine {
  itemId: string;
  sku: string;
  name: string;
  unitMinor: string;
  taxBp: number;
  qty: number;
}

export interface Tab {
  id: string;
  label: string;
  openedAt: number;
  lines: TabLine[];
}

/** One journal entry as stored. Digests and the signature are lowercase hex. */
export interface JournalRecord {
  sequence: number;
  terminalId: string;
  epochSecond: number;
  nano: number;
  sale: SaleBody;
  bodyDigest: string;
  previousDigest: string;
  digest: string;
  signature: string;
}

export interface JournalHead {
  lastSequence: number;
  headDigest: string;
  genesisDigest: string;
  lastEpochSecond: number;
  lastNano: number;
}

export interface StoredLease {
  /** the server's id for this lease; absent on a lease installed before sync existed */
  leaseId?: string;
  terminalId: string;
  firstNumber: string;
  lastNumber: string;
  nextNumber: string;
  issuedAtMs: number;
  expiresAtMs: number;
}

/** Where this terminal's journal stands with the server. Terminal-local bookkeeping, not signed. */
export interface SyncState {
  /** highest sequence the server has confirmed it holds, verified and chained */
  syncedThrough: number;
  lastAttemptMs: number | null;
  lastSuccessMs: number | null;
  lastError: string | null;
  /** the server is holding this terminal's later entries behind a missing sequence */
  heldAtGap: boolean;
  openExceptions: number;
}

/** A lease the terminal replaced and still owes the server the unused tail of. */
export interface PendingReturn {
  leaseId: string;
  nextUnused: string;
}
