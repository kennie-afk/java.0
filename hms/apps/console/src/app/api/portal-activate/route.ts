import { NextResponse } from "next/server";

const API = process.env.HMS_API_URL ?? "http://localhost:8100";

export async function POST(request: Request) {
  const body = await request.json().catch(() => null);
  const res = await fetch(`${API}/portal/auth/activate`, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body ?? {}), cache: "no-store" });
  const data = await res.json().catch(() => ({}));
  return NextResponse.json(data, { status: res.status });
}
