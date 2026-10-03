import { createCipheriv, createDecipheriv, createHash, randomBytes } from "node:crypto";

/**
 * The sign-in cookie. It holds the operator credential, so it is encrypted (AES-256-GCM, key derived from
 * OFFICE_SESSION_SECRET) and authenticated, not merely signed: reading a cookie out of a log or a proxy cache
 * must not hand over a credential. It carries its own expiry, checked on every open.
 */
export interface Session {
  credential: string;
  /** Only for a platform credential, which has no tenant of its own and must name one. */
  tenant: string | null;
  /** epoch milliseconds */
  exp: number;
}

export const COOKIE = "mara_office";
export const SESSION_HOURS = 8;

export function sessionKey(secret: string | undefined = process.env.OFFICE_SESSION_SECRET): Buffer {
  if (!secret || secret.length < 32) {
    throw new Error("OFFICE_SESSION_SECRET must be set to at least 32 characters");
  }
  return createHash("sha256").update(secret).digest();
}

export function seal(session: Session, key: Buffer = sessionKey()): string {
  const iv = randomBytes(12);
  const cipher = createCipheriv("aes-256-gcm", key, iv);
  const body = Buffer.concat([cipher.update(JSON.stringify(session), "utf8"), cipher.final()]);
  return Buffer.concat([iv, cipher.getAuthTag(), body]).toString("base64url");
}

/** The session, or null for anything tampered with, malformed, expired or sealed under another key. */
export function open(token: string | undefined, now: number = Date.now(), key: Buffer = sessionKey()): Session | null {
  if (!token || token.length > 4096) return null;
  try {
    const raw = Buffer.from(token, "base64url");
    if (raw.length < 12 + 16 + 2) return null;
    const decipher = createDecipheriv("aes-256-gcm", key, raw.subarray(0, 12));
    decipher.setAuthTag(raw.subarray(12, 28));
    const text = Buffer.concat([decipher.update(raw.subarray(28)), decipher.final()]).toString("utf8");
    const s = JSON.parse(text) as Session;
    if (typeof s.credential !== "string" || typeof s.exp !== "number" || s.exp <= now) return null;
    if (s.tenant !== null && typeof s.tenant !== "string") return null;
    return s;
  } catch {
    return null;
  }
}
