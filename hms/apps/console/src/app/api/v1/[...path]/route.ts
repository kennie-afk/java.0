import { cookies } from "next/headers";
import { NextResponse, type NextRequest } from "next/server";

const API = process.env.HMS_API_URL ?? "http://localhost:8100";

// Forwards the browser's call to the API with the token from the httpOnly cookie. Same-origin only:
// the browser never holds the token and the API needs no CORS for the console.
async function forward(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  const { path } = await context.params;
  const jar = await cookies();
  const token = jar.get("hms_token")?.value;
  if (!token) {
    return NextResponse.json({ code: "unauthorized", detail: "Sign in to continue." }, { status: 401 });
  }
  const url = `${API}/v1/${path.join("/")}${request.nextUrl.search}`;
  const headers: Record<string, string> = { Authorization: `Bearer ${token}` };
  const reason = request.headers.get("x-access-reason");
  if (reason) headers["X-Access-Reason"] = reason;
  const idem = request.headers.get("idempotency-key");
  if (idem) headers["Idempotency-Key"] = idem;
  const hasBody = request.method !== "GET" && request.method !== "HEAD";
  if (hasBody) headers["Content-Type"] = "application/json";
  const res = await fetch(url, { method: request.method, headers, body: hasBody ? await request.text() : undefined, cache: "no-store" });
  const text = await res.text();
  return new NextResponse(text.length ? text : null, {
    status: res.status,
    headers: { "Content-Type": res.headers.get("content-type") ?? "application/json", ...(res.headers.get("idempotent-replay") ? { "Idempotent-Replay": "true" } : {}) }
  });
}

export { forward as GET, forward as POST, forward as PUT, forward as DELETE };
