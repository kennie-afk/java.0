import { cookies } from "next/headers";
import { NextResponse } from "next/server";

const API = process.env.HMS_API_URL ?? "http://localhost:8100";
const secure = process.env.HMS_COOKIE_SECURE === "true";

// The API token lives only in an httpOnly cookie, so page scripts can never read it.
const cookieOptions = (maxAge: number) => ({ httpOnly: true, sameSite: "strict" as const, secure, path: "/", maxAge });

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
  jar.set("hms_token", data.token, cookieOptions(data.expiresInSeconds ?? 3600));
  jar.set("hms_user", encodeURIComponent(data.fullName ?? ""), cookieOptions(data.expiresInSeconds ?? 3600));
  const { token: _token, ...session } = data;
  return NextResponse.json(session);
}

export async function GET() {
  const jar = await cookies();
  const token = jar.get("hms_token")?.value;
  if (!token) {
    return NextResponse.json({ code: "unauthorized" }, { status: 401 });
  }
  const res = await fetch(`${API}/v1/auth/me`, { headers: { Authorization: `Bearer ${token}` }, cache: "no-store" });
  if (!res.ok) {
    return NextResponse.json({ code: "unauthorized" }, { status: 401 });
  }
  const me = await res.json();
  return NextResponse.json({ ...me, fullName: decodeURIComponent(jar.get("hms_user")?.value ?? "") });
}

export async function DELETE() {
  const jar = await cookies();
  jar.delete("hms_token");
  jar.delete("hms_user");
  return NextResponse.json({ ok: true });
}
