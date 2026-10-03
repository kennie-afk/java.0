/** A fixed-window counter per key, for the one unauthenticated endpoint here (sign-in). Per process. */
export class Limiter {
  private windows = new Map<string, { start: number; count: number }>();

  constructor(private readonly limit: number, private readonly windowMs: number, private readonly maxKeys = 10_000) {}

  /** true when the request may proceed */
  take(key: string, now: number = Date.now()): boolean {
    if (this.windows.size >= this.maxKeys && !this.windows.has(key)) {
      for (const [k, w] of this.windows) if (now - w.start >= this.windowMs) this.windows.delete(k);
      if (this.windows.size >= this.maxKeys) key = "\u0000overflow";
    }
    const w = this.windows.get(key);
    if (!w || now - w.start >= this.windowMs) {
      this.windows.set(key, { start: now, count: 1 });
      return true;
    }
    if (w.count >= this.limit) return false;
    w.count++;
    return true;
  }
}

/** The address to count a request against: the last X-Forwarded-For entry (appended by the ingress) when present. */
export function clientKey(forwardedFor: string | null): string {
  if (forwardedFor) {
    const last = forwardedFor.split(",").pop()?.trim() ?? "";
    if (/^[0-9a-fA-F:.]{2,45}$/.test(last)) return last;
  }
  return "unknown";
}
