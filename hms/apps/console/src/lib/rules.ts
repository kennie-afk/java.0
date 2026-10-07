/**
 * Small client-side rules for the forms that change things that cannot be undone or that touch credentials. They only
 * stop an obviously wrong request from being sent: the server enforces every one of them again.
 */

export const MERGE_WORD = "MERGE";
export const MERGE_REASON_MIN = 10;
export const PASSWORD_MIN = 12;

/** A merge is sent only when the person has typed the word, given a real reason, and picked a different record. */
export function mergeReady(o: { typed: string; reason: string; duplicateId: string; survivorId?: string | null }): boolean {
  return o.typed.trim() === MERGE_WORD && o.reason.trim().length >= MERGE_REASON_MIN && !!o.survivorId && o.survivorId !== o.duplicateId;
}

/** The first thing wrong with a password change, or null. Matches the server's 12-character minimum. */
export function passwordProblem(current: string, next: string, confirm: string): string | null {
  if (!current) return "Enter your current password.";
  if (next.length < PASSWORD_MIN) return `The new password must be at least ${PASSWORD_MIN} characters.`;
  if (next === current) return "The new password must differ from the current one.";
  if (next !== confirm) return "The two new passwords do not match.";
  return null;
}

export const ADJUSTMENT_REASONS = ["ADJUSTMENT", "WRITE_OFF", "RETURN"] as const;
export type AdjustmentReason = (typeof ADJUSTMENT_REASONS)[number];

/** Mirrors the server: no zero change, a write-off only reduces stock, a note of at least 5 characters, never below zero. */
export function adjustmentProblem(o: { delta: number; reason: AdjustmentReason; note: string; onHand: number }): string | null {
  if (!Number.isFinite(o.delta) || o.delta === 0) return "Enter a quantity that changes the stock.";
  if (o.reason === "WRITE_OFF" && o.delta > 0) return "A write-off reduces stock.";
  if (o.onHand + o.delta < 0) return `That would take the batch below zero (on hand ${o.onHand}).`;
  if (o.note.trim().length < 5) return "Say why, in at least 5 characters.";
  return null;
}

/** Body of POST /v1/scheduling/appointments/{id}/reschedule: the new start and the version the person was looking at. */
export const rescheduleBody = (slotStart: string, version: number) => ({ startsAt: slotStart, version });

/** Demographics + identifiers for POST /v1/patients/duplicate-check, built from a loaded patient record. */
export function duplicateCheckBody(p: { givenName: string; otherNames?: string; familyName: string; sex: string; birthDate: string; phone?: string; identifiers: { system: string; value: string }[] }) {
  return {
    demographics: { givenName: p.givenName, otherNames: p.otherNames || undefined, familyName: p.familyName, sex: p.sex, birthDate: p.birthDate, phone: p.phone || undefined },
    identifiers: p.identifiers.filter((i) => i.system !== "MRN").map((i) => ({ system: i.system, value: i.value }))
  };
}

/** An empty requirement is open to every signed-in person; anything else must have been granted by the server. */
export const permitted = (granted: Iterable<string>, required: string) => !required || new Set(granted).has(required);
