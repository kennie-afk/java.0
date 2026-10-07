import { cookies } from "next/headers";
import { NextResponse } from "next/server";
import { clearSessionCookies, COOKIE, refreshTokens, setSessionCookies, type Tokens } from "@/lib/auth-server";

const API = process.env.HMS_API_URL ?? "http://localhost:8100";
const secure = process.env.HMS_COOKIE_SECURE === "true";

// The API tokens live only in httpOnly cookies, so page scripts can never read them (see lib/auth-server.ts).
export async function POST(request: Request) {
  const body = await request.json().catch(() => null);
  const res = await fetch(`${API}/v1/auth/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body ?? {}),
    cache: "no-store"
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) {
    return NextResponse.json(data, { status: res.status });
  }
  const jar = await cookies();
  setSessionCookies(jar, data as Tokens, secure, data.fullName ?? "");
  const { token: _token, refreshToken: _refresh, ...session } = data;
  return NextResponse.json(session);
}

export async function GET() {
  const jar = await cookies();
  let token = jar.get(COOKIE.access)?.value;
  const refresh = jar.get(COOKIE.refresh)?.value;
  let fresh: Tokens | undefined;
  const signedOut = () => {
    const out = NextResponse.json({ code: "unauthorized" }, { status: 401 });
    clearSessionCookies(out.cookies);
    return out;
  };
  const renew = async () => {
    if (!refresh) return signedOut();
    const r = await refreshTokens(API, refresh);
    if (!r.ok) return r.reason === "unavailable" ? NextResponse.json({ code: "unavailable" }, { status: 503 }) : signedOut();
    fresh = r.tokens;
    token = r.tokens.token;
    return null;
  };
  if (!token) {
    const failed = await renew();
    if (failed) return failed;
  }
  let res = await fetch(`${API}/v1/auth/me`, { headers: { Authorization: `Bearer ${token}` }, cache: "no-store" });
  if (res.status === 401 && refresh && !fresh) {
    const failed = await renew();
    if (failed) return failed;
    res = await fetch(`${API}/v1/auth/me`, { headers: { Authorization: `Bearer ${token}` }, cache: "no-store" });
  }
  if (!res.ok) return res.status >= 500 ? NextResponse.json({ code: "unavailable" }, { status: 503 }) : signedOut();
  const me = await res.json();
  const out = NextResponse.json({ ...me, fullName: decodeURIComponent(jar.get(COOKIE.user)?.value ?? "") });
  if (fresh) setSessionCookies(out.cookies, fresh, secure);
  return out;
}

// Signing out revokes this device's refresh chain on the API, so a copied cookie cannot be used afterwards.
export async function DELETE() {
  const jar = await cookies();
  const refresh = jar.get(COOKIE.refresh)?.value;
  if (refresh) {
    await fetch(`${API}/v1/auth/logout`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ refreshToken: refresh }), cache: "no-store" }).catch(() => null);
  }
  clearSessionCookies(jar);
  return NextResponse.json({ ok: true });
}
