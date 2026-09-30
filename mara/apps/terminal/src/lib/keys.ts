/**
 * The terminal's Ed25519 identity, via WebCrypto.
 *
 * The private key is generated with extractable=false: script running on this page can
 * ask the browser to sign with it but can never read its bytes, and it is persisted as a
 * CryptoKey handle in IndexedDB rather than as key material. (A public key is always
 * exportable in WebCrypto regardless of that flag; only its SPKI form leaves the device.)
 */
import { fromBase64, fromHex, toBase64, toHex } from "./bytes";

export class Ed25519UnsupportedError extends Error {
  constructor() {
    super(
      "This browser does not support Ed25519 in WebCrypto (needs Chrome 137+, Firefox 129+ or Safari 17+), " +
        "or the page is not a secure context (HTTPS or localhost). The terminal cannot generate its key."
    );
    this.name = "Ed25519UnsupportedError";
  }
}

export interface GeneratedKey {
  privateKey: CryptoKey;
  publicKey: CryptoKey;
  /** X.509 SubjectPublicKeyInfo, base64: the exact form identity-service stores. */
  publicKeySpkiBase64: string;
}

export async function generateTerminalKey(): Promise<GeneratedKey> {
  let pair: CryptoKeyPair;
  try {
    pair = (await crypto.subtle.generateKey({ name: "Ed25519" }, false, ["sign", "verify"])) as CryptoKeyPair;
  } catch {
    throw new Ed25519UnsupportedError();
  }
  const spki = new Uint8Array(await crypto.subtle.exportKey("spki", pair.publicKey));
  return { privateKey: pair.privateKey, publicKey: pair.publicKey, publicKeySpkiBase64: toBase64(spki) };
}

export async function signDigest(privateKey: CryptoKey, digest: Uint8Array): Promise<string> {
  const sig = await crypto.subtle.sign({ name: "Ed25519" }, privateKey, new Uint8Array(digest));
  return toHex(new Uint8Array(sig));
}

export async function verifyDigest(
  publicKeySpkiBase64: string,
  digest: Uint8Array,
  signatureHex: string
): Promise<boolean> {
  try {
    const key = await crypto.subtle.importKey(
      "spki",
      new Uint8Array(fromBase64(publicKeySpkiBase64)),
      { name: "Ed25519" },
      true,
      ["verify"]
    );
    return await crypto.subtle.verify(
      { name: "Ed25519" },
      key,
      new Uint8Array(fromHex(signatureHex)),
      new Uint8Array(digest)
    );
  } catch {
    return false;
  }
}
