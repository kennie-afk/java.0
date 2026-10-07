/**
 * A fresh random key for one logical action. crypto.randomUUID exists only in secure contexts
 * (https or localhost), so a console served over plain http falls back to getRandomValues.
 */
export function newKey(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (b) => b.toString(16).padStart(2, "0")).join("");
}
