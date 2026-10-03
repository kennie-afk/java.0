import { NextResponse } from "next/server";
import { bases, currentSession, sameOrigin, secureCookie } from "@/lib/guard";
import { clientKey, Limiter } from "@/lib/limit";
import { COOKIE, SESSION_HOURS, seal } from "@/lib/session";

export const dynamic = "force-dynamic";

const attempts = new Limiter(10, 60_000);
const CREDENTIAL = /^mop_[0-9a-f]{16}\.[A-Za-z0-9_-]{43}$/;

/** Who is signed in (never the credential): used by the shell to decide whether to show the sign-in page. */
export async function GET() {
  const s = await currentSession();
  return s ? NextResponse.json({ signedIn: true, tenant: s.tenant }) : NextResponse.json({ signedIn: false }, { status: 401 });
}

/**
 * Signs in by presenting an operator credential, which is checked against identity-service before anything is stored.
 * A tenant-bound credential needs nothing else; a platform credential names the tenant to look at.
 */
export async function POST(request: Request) {
  if (!sameOrigin(request)) return NextResponse.json({ error: "forbidden" }, { status: 403 });
  if (!attempts.take(clientKey(request.headers.get("x-forwarded-for")))) {
    return NextResponse.json({ error: "rate_limited", message: "Too many attempts. Wait a minute." }, { status: 429, headers: { "retry-after": "60" } });
  }
  let body: { credential?: unknown; tenant?: unknown };
  try {
    body = await request.json();
  } catch {
    return NextResponse.json({ error: "bad_request" }, { status: 400 });
  }
  const credential = typeof body.credential === "string" ? body.credential.trim() : "";
  const tenant = typeof body.tenant === "string" && body.tenant.trim() ? body.tenant.trim().slice(0, 60) : null;
  if (!CREDENTIAL.test(credential)) {
    return NextResponse.json({ error: "malformed", message: "That is not a Mara credential (it starts mop_)." }, { status: 400 });
  }
  const base = bases().identity;
  if (!base) return NextResponse.json({ error: "not_configured" }, { status: 502 });
  if (!process.env.OFFICE_SESSION_SECRET || process.env.OFFICE_SESSION_SECRET.length < 32) {
    return NextResponse.json({ error: "not_configured", message: "OFFICE_SESSION_SECRET is not set on the server." }, { status: 503 });
  }
  let res: Response;
  try {
    res = await fetch(new URL("/v1/admin/branches", base), {
      headers: { authorization: `Bearer ${credential}`, ...(tenant ? { "x-mara-tenant": tenant } : {}) },
      signal: AbortSignal.timeout(10_000), cache: "no-store"
    });
  } catch {
    return NextResponse.json({ error: "unreachable", message: "The Mara service did not answer." }, { status: 502 });
  }
  const text = await res.text();
  // The credential filter answers {"error":"unauthorised"}; identity's own tenant filter answers 401 with no such body
  // when a valid platform credential names no tenant. The two must not be confused: one is a wrong credential, the
  // other just needs a tenant.
  const refusedByFilter = (res.status === 401 && text.includes("unauthorised")) || res.status === 403;
  if (refusedByFilter) {
    return NextResponse.json({ error: "refused", message: "That credential was not accepted (wrong, expired or revoked)." }, { status: 401 });
  }
  if (res.status !== 200) {
    return NextResponse.json({ error: "needs_tenant", message: "This credential is not tied to one shop: enter the tenant id to look at." }, { status: 400 });
  }
  const token = seal({ credential, tenant, exp: Date.now() + SESSION_HOURS * 3_600_000 });
  const out = NextResponse.json({ signedIn: true, tenant });
  out.cookies.set(COOKIE, token, { httpOnly: true, sameSite: "strict", secure: secureCookie(request), path: "/", maxAge: SESSION_HOURS * 3600 });
  return out;
}

export async function DELETE(request: Request) {
  if (!sameOrigin(request)) return NextResponse.json({ error: "forbidden" }, { status: 403 });
  const out = NextResponse.json({ signedIn: false });
  out.cookies.set(COOKIE, "", { httpOnly: true, sameSite: "strict", secure: secureCookie(request), path: "/", maxAge: 0 });
  return out;
}
