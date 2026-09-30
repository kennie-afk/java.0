import { NextResponse } from "next/server";

/**
 * Same-origin proxy to identity-service's staff sign-in, for the same reason the enrolment
 * proxy exists: the browser makes no cross-origin call to the trust root. It forwards the
 * terminal's signed attempt unchanged and passes the answer back; it holds no secret, keeps
 * nothing, and cannot forge the signature (the private key never leaves the browser).
 */
export const dynamic = "force-dynamic";

const TERMINAL_ID = /^TERM-[0-9A-F]{20}$/;

export async function POST(request: Request) {
  const base = process.env.IDENTITY_BASE_URL;
  if (!base) {
    return NextResponse.json({ error: "identity_unreachable", message: "IDENTITY_BASE_URL is not configured." }, { status: 502 });
  }
  let input: unknown;
  try {
    input = await request.json();
  } catch {
    return NextResponse.json({ error: "bad_request", message: "Body must be JSON." }, { status: 400 });
  }
  const { terminalId, staffNumber, pin, timestamp, signature } = (input ?? {}) as Record<string, unknown>;
  if (
    typeof terminalId !== "string" || !TERMINAL_ID.test(terminalId) ||
    typeof staffNumber !== "string" || !/^[A-Za-z0-9-]{1,20}$/.test(staffNumber) ||
    typeof pin !== "string" || pin.length < 1 || pin.length > 12 ||
    typeof timestamp !== "number" || !Number.isFinite(timestamp) ||
    typeof signature !== "string" || signature.length > 200
  ) {
    return NextResponse.json({ error: "bad_request", message: "Malformed sign-in request." }, { status: 400 });
  }
  try {
    const upstream = await fetch(new URL(`/v1/terminals/${terminalId}/staff-signin`, base), {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ staffNumber, pin, timestamp, signature }),
      signal: AbortSignal.timeout(10_000),
      cache: "no-store"
    });
    const text = await upstream.text();
    return new NextResponse(text, {
      status: upstream.status,
      headers: { "content-type": upstream.headers.get("content-type") ?? "application/json" }
    });
  } catch {
    return NextResponse.json({ error: "identity_unreachable", message: "identity-service did not answer." }, { status: 502 });
  }
}
