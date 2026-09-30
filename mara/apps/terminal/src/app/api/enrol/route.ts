import { NextResponse } from "next/server";

/**
 * Same-origin proxy to identity-service's POST /v1/enrolment, so the browser never makes
 * a cross-origin call (no CORS surface on the trust root). It forwards exactly the three
 * documented fields and passes the service's status and body back unchanged. It holds no
 * secret and stores nothing.
 */
export const dynamic = "force-dynamic";

const CODE_MAX = 40;
const KEY_MAX = 200;
const LABEL_MAX = 60;

export async function POST(request: Request) {
  const base = process.env.IDENTITY_BASE_URL;
  if (!base) {
    return NextResponse.json(
      { error: "identity_unreachable", message: "IDENTITY_BASE_URL is not configured on this terminal's server." },
      { status: 502 }
    );
  }

  let input: unknown;
  try {
    input = await request.json();
  } catch {
    return NextResponse.json({ error: "bad_request", message: "Body must be JSON." }, { status: 400 });
  }
  const { code, publicKey, label } = (input ?? {}) as Record<string, unknown>;
  const valid = (v: unknown, max: number): v is string => typeof v === "string" && v.trim() !== "" && v.length <= max;
  if (!valid(code, CODE_MAX) || !valid(publicKey, KEY_MAX) || !valid(label, LABEL_MAX)) {
    return NextResponse.json({ error: "bad_request", message: "code, publicKey and label are required." }, { status: 400 });
  }

  try {
    const upstream = await fetch(new URL("/v1/enrolment", base), {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ code, publicKey, label }),
      signal: AbortSignal.timeout(10_000),
      cache: "no-store"
    });
    const text = await upstream.text();
    return new NextResponse(text, {
      status: upstream.status,
      headers: { "content-type": upstream.headers.get("content-type") ?? "application/json" }
    });
  } catch {
    return NextResponse.json(
      { error: "identity_unreachable", message: "identity-service did not answer." },
      { status: 502 }
    );
  }
}
