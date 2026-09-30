// Signs a digest with a fresh WebCrypto Ed25519 key and writes it for Vectors.java to verify
// with the platform's real TerminalSignature. Usage: node vectors/export-ts-signature.mjs
import { writeFileSync } from "node:fs";
const pair = await crypto.subtle.generateKey({ name: "Ed25519" }, false, ["sign", "verify"]);
const message = new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode("mara webcrypto signature check")));
const sig = new Uint8Array(await crypto.subtle.sign({ name: "Ed25519" }, pair.privateKey, message));
const hex = (b) => Buffer.from(b).toString("hex");
const spki = Buffer.from(await crypto.subtle.exportKey("spki", pair.publicKey)).toString("base64");
writeFileSync(new URL("./ts-signature.json", import.meta.url), JSON.stringify({ publicKeySpkiBase64: spki, message: hex(message), signature: hex(sig) }));
