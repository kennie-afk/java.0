import { cookies } from "next/headers";
import { NextResponse } from "next/server";

const API = process.env.HMS_API_URL ?? "http://localhost:8100";
const secure = process.env.HMS_COOKIE_SECURE === "true";
const cookieOptions = (maxAge: number) => ({ httpOnly: true, sameSite: "strict" as const, secure, path: "/", maxAge });

// A patient session is a separate cookie from a staff session, so one can never stand in for the other.
export async function POST(request: Request) {
  const body = await request.json().catch(() => null);
  const res = await fetch(`${API}/portal/auth/login`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body ?? {}), cache: "no-store" });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) return NextResponse.json(data, { status: res.status });
  const jar = await cookies();
  jar.set("hms_portal", data.token, cookieOptions(data.expiresInSeconds ?? 1800));
  jar.set("hms_portal_user", encodeURIComponent(data.patientName ?? ""), cookieOptions(data.expiresInSeconds ?? 1800));
  const { token: _token, ...session } = data;
  return NextResponse.json(session);
}

export async function GET() {
  const jar = await cookies();
  const token = jar.get("hms_portal")?.value;
  if (!token) return NextResponse.json({ code: "unauthorized" }, { status: 401 });
  const res = await fetch(`${API}/portal/me`, { headers: { Authorization: `Bearer ${token}` }, cache: "no-store" });
  if (!res.ok) return NextResponse.json({ code: "unauthorized" }, { status: 401 });
  return NextResponse.json({ patientName: decodeURIComponent(jar.get("hms_portal_user")?.value ?? "") });
}

export async function DELETE() {
  const jar = await cookies();
  jar.delete("hms_portal");
  jar.delete("hms_portal_user");
  return NextResponse.json({ ok: true });
}
