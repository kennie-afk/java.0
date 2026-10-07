// Server-side session handling for the console's own API routes (never imported by browser code).
//
// The browser holds two httpOnly cookies and never sees either token: a short access token (hms_token, expires with the token) and a
// refresh token (hms_refresh, sent only to /api). When the access cookie is gone or the API says 401, the route refreshes once and carries on, so a
// request, and a queued offline write replayed after the token expired, simply succeeds. A refresh token works only once on the API, so
// parallel requests from one browser must not each use it: they share one in-flight refresh and, for a few seconds after, its result.

export type Tokens = { token: string; expiresInSeconds: number; refreshToken?: string; refreshExpiresInSeconds: number };
export type RefreshOutcome = { ok: true; tokens: Tokens } | { ok: false; reason: "invalid" | "unavailable" };

const SHARE_MS = 15_000;
const shared = new Map<string, { at: number; result: Promise<RefreshOutcome> }>();

export function refreshTokens(api: string, refreshToken: string, doFetch: typeof fetch = fetch): Promise<RefreshOutcome> {
  const now = Date.now();
  for (const [k, v] of shared) if (now - v.at > SHARE_MS) shared.delete(k);
  const hit = shared.get(refreshToken);
  if (hit) return hit.result;
  const result = (async (): Promise<RefreshOutcome> => {
    let res: Response;
    try {
      res = await doFetch(`${api}/v1/auth/refresh`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ refreshToken }), cache: "no-store" });
    } catch {
      return { ok: false, reason: "unavailable" };
    }
    if (res.status === 401 || res.status === 400) return { ok: false, reason: "invalid" };
    // A server error or rate limit is not the same as being signed out: keep the cookies and let the caller try again.
    if (!res.ok) return { ok: false, reason: "unavailable" };
    const body = (await res.json().catch(() => null)) as Tokens | null;
    if (!body || typeof body.token !== "string") return { ok: false, reason: "unavailable" };
    return { ok: true, tokens: body };
  })();
  shared.set(refreshToken, { at: now, result });
  // A failed attempt is not worth sharing: the next request should try again.
  void result.then((r) => { if (!r.ok && r.reason === "unavailable") shared.delete(refreshToken); });
  return result;
}

export const COOKIE = { access: "hms_token", refresh: "hms_refresh", user: "hms_user" } as const;

type CookieJar = { set: (name: string, value: string, options: Record<string, unknown>) => unknown; delete: (name: string) => unknown };

const base = (secure: boolean) => ({ httpOnly: true, sameSite: "strict" as const, secure });

/** Writes the cookies for a fresh set of tokens. A refresh answer without a new refresh token (a parallel tab got there first) leaves that cookie alone. */
export function setSessionCookies(jar: CookieJar, tokens: Tokens, secure: boolean, fullName?: string) {
  jar.set(COOKIE.access, tokens.token, { ...base(secure), path: "/", maxAge: tokens.expiresInSeconds });
  if (tokens.refreshToken) jar.set(COOKIE.refresh, tokens.refreshToken, { ...base(secure), path: "/api", maxAge: tokens.refreshExpiresInSeconds });
  if (fullName !== undefined) jar.set(COOKIE.user, encodeURIComponent(fullName), { ...base(secure), path: "/", maxAge: tokens.refreshExpiresInSeconds });
}

export function clearSessionCookies(jar: CookieJar) {
  jar.delete(COOKIE.access);
  jar.delete(COOKIE.refresh);
  jar.delete(COOKIE.user);
}

/** Test hook: forget shared refreshes. */
export const _resetShared = () => shared.clear();
