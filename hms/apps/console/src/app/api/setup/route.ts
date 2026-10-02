import { NextResponse } from "next/server";

const API = process.env.HMS_API_URL ?? "http://localhost:8100";

// First-run onboarding: creates an organisation, its first facility and its administrator. Public by design
// (nothing exists to sign in to yet); the API rate-limits it per address.
export async function POST(request: Request) {
  const res = await fetch(`${API}/v1/organisations`, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...(request.headers.get("x-forwarded-for") ? { "X-Forwarded-For": request.headers.get("x-forwarded-for")! } : {}) },
    body: await request.text(),
    cache: "no-store"
  });
  const text = await res.text();
  return new NextResponse(text || null, { status: res.status, headers: { "Content-Type": res.headers.get("content-type") ?? "application/json" } });
}
