import { NextResponse } from "next/server";

export const dynamic = "force-dynamic";

/** Whether identity-service answers its health probe. Reports facts, never guesses. */
export async function GET() {
  const base = process.env.IDENTITY_BASE_URL;
  if (!base) return NextResponse.json({ configured: false, reachable: false });
  try {
    const r = await fetch(new URL("/actuator/health", base), { signal: AbortSignal.timeout(2500), cache: "no-store" });
    return NextResponse.json({ configured: true, reachable: r.ok, httpStatus: r.status });
  } catch {
    return NextResponse.json({ configured: true, reachable: false });
  }
}
