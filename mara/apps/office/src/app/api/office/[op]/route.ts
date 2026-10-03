import { NextResponse } from "next/server";
import { bases, currentSession, sameOrigin } from "@/lib/guard";
import { build } from "@/lib/routes";
import { COOKIE } from "@/lib/session";

export const dynamic = "force-dynamic";

async function handle(request: Request, ctx: { params: Promise<{ op: string }> }) {
  const session = await currentSession();
  if (!session) return NextResponse.json({ error: "not_signed_in" }, { status: 401 });
  if (request.method !== "GET" && !sameOrigin(request)) return NextResponse.json({ error: "forbidden" }, { status: 403 });

  const { op } = await ctx.params;
  const bodyText = request.method === "POST" ? await request.text() : undefined;
  const built = build(op, request.method, new URL(request.url).searchParams, bodyText, bases());
  if (!built.ok) return NextResponse.json({ error: built.error }, { status: built.status });

  let upstream: Response;
  try {
    upstream = await fetch(built.url, {
      method: request.method,
      headers: {
        authorization: `Bearer ${session.credential}`,
        ...(session.tenant ? { "x-mara-tenant": session.tenant } : {}),
        ...(built.body !== undefined ? { "content-type": "application/json" } : {})
      },
      body: built.body,
      signal: AbortSignal.timeout(20_000),
      cache: "no-store"
    });
  } catch {
    return NextResponse.json({ error: "unreachable", message: "The Mara service did not answer." }, { status: 502 });
  }
  const text = await upstream.text();
  if (upstream.status === 401) {
    // the credential expired or was revoked: end the session so the next page load asks again
    const out = NextResponse.json({ error: "not_signed_in", message: "Your credential is no longer valid." }, { status: 401 });
    out.cookies.set(COOKIE, "", { httpOnly: true, sameSite: "strict", path: "/", maxAge: 0 });
    return out;
  }
  return new NextResponse(text || null, {
    status: upstream.status,
    headers: { "content-type": upstream.headers.get("content-type") ?? "application/json" }
  });
}

export const GET = handle;
export const POST = handle;
