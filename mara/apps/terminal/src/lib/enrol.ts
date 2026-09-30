import { generateTerminalKey } from "./keys";
import { saveIdentityOnce } from "./terminal-store";

export type EnrolResult =
  | { kind: "enrolled"; terminalId: string }
  | { kind: "refused"; error: string; message: string }
  | { kind: "malformed"; detail: string }
  | { kind: "unreachable"; detail: string }
  | { kind: "offline" }
  | { kind: "unexpected"; status: number; detail: string };

export const CODE_MAX = 40;
export const LABEL_MAX = 60;

/**
 * Generates the terminal key on this device and presents only its PUBLIC half to
 * identity-service through the same-origin proxy. The key pair is kept only if the
 * service accepts; a refused attempt leaves nothing behind.
 */
export async function enrol(code: string, label: string): Promise<EnrolResult> {
  if (typeof navigator !== "undefined" && !navigator.onLine) return { kind: "offline" };
  const key = await generateTerminalKey();

  let response: Response;
  try {
    response = await fetch("/api/enrol", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ code, publicKey: key.publicKeySpkiBase64, label })
    });
  } catch {
    return { kind: "unreachable", detail: "The request never reached this terminal's own server." };
  }

  const body = (await response.json().catch(() => null)) as Record<string, unknown> | null;

  if (response.status === 201 && body && typeof body.terminalId === "string") {
    await saveIdentityOnce({
      terminalId: body.terminalId,
      label: label.trim(),
      publicKeySpkiBase64: key.publicKeySpkiBase64,
      privateKey: key.privateKey,
      publicKey: key.publicKey,
      enrolledAt: Date.now()
    });
    return { kind: "enrolled", terminalId: body.terminalId };
  }
  if (response.status === 403 && body && typeof body.message === "string") {
    return { kind: "refused", error: String(body.error ?? "enrolment_refused"), message: body.message };
  }
  if (response.status === 502 && body?.error === "identity_unreachable") {
    return { kind: "unreachable", detail: String(body.message ?? "identity-service could not be reached.") };
  }
  if (response.status === 400) {
    return { kind: "malformed", detail: "identity-service rejected the request as malformed (HTTP 400)." };
  }
  return { kind: "unexpected", status: response.status, detail: JSON.stringify(body) ?? "" };
}
