import { NextResponse } from "next/server";

/**
 * Same-origin forwarding of a terminal's already-signed request to a Mara server.
 *
 * The browser signs; this holds no secret and cannot forge or alter anything (the body is
 * forwarded byte for byte, and a changed byte fails the server's signature check). It exists
 * so the browser makes no cross-origin call and the service addresses stay server-side. Each
 * route maps to ONE fixed upstream path, so it is not an open proxy.
 */
export const SIGNED_HEADERS = ["x-mara-terminal", "x-mara-timestamp", "x-mara-signature"] as const;

export async function forward(
  request: Request,
  service: "SYNC_BASE_URL" | "CORE_BASE_URL",
  method: "GET" | "POST",
  upstreamPath: string
): Promise<NextResponse> {
  const base = process.env[service];
  if (!base) {
    return NextResponse.json({ error: "not_configured", message: `${service} is not configured.` }, { status: 502 });
  }
  const headers: Record<string, string> = { "content-type": "application/json" };
  for (const h of SIGNED_HEADERS) {
    const v = request.headers.get(h);
    if (!v || v.length > 300) return NextResponse.json({ error: "bad_request", message: "Missing request signature." }, { status: 400 });
    headers[h] = v;
  }
  const body = method === "POST" ? await request.text() : undefined;
  if (body !== undefined && body.length > 4 * 1024 * 1024) {
    return NextResponse.json({ error: "too_large" }, { status: 413 });
  }
  try {
    const upstream = await fetch(new URL(upstreamPath, base), {
      method,
      headers,
      body,
      signal: AbortSignal.timeout(20_000),
      cache: "no-store"
    });
    const text = await upstream.text();
    return new NextResponse(text, {
      status: upstream.status,
      headers: { "content-type": upstream.headers.get("content-type") ?? "application/json" }
    });
  } catch {
    return NextResponse.json({ error: "upstream_unreachable", message: "The service did not answer." }, { status: 502 });
  }
}
