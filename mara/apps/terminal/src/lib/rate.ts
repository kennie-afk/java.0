/** "16" -> 1600, "16.5" -> 1650, "0.25" -> 25. Exact decimal-string arithmetic; no floats. */
export function percentToBasisPoints(input: string): number | null {
  const m = /^(\d{1,3})(?:\.(\d{1,2}))?$/.exec(input.trim());
  if (!m) return null;
  const bp = Number(m[1]) * 100 + Number((m[2] ?? "").padEnd(2, "0") || "0");
  return bp > 10_000 ? null : bp;
}

export function basisPointsToPercent(bp: number): string {
  const whole = Math.floor(bp / 100);
  const frac = bp % 100;
  return frac === 0 ? String(whole) : `${whole}.${String(frac).padStart(2, "0").replace(/0$/, "")}`;
}
