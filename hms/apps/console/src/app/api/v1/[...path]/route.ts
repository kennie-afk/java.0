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
  // A file upload is passed through byte for byte with its own boundary; everything else is JSON text.
  const upload = (request.headers.get("content-type") ?? "").startsWith("multipart/form-data");
  if (hasBody) headers["Content-Type"] = upload ? request.headers.get("content-type")! : "application/json";
  const res = await fetch(url, { method: request.method, headers, body: hasBody ? (upload ? await request.arrayBuffer() : await request.text()) : undefined, cache: "no-store" });
  // Read as bytes, not text, so an image comes back intact.
  const bytes = await res.arrayBuffer();
  const out: Record<string, string> = { "Content-Type": res.headers.get("content-type") ?? "application/json" };
  if (res.headers.get("idempotent-replay")) out["Idempotent-Replay"] = "true";
  // Patient images stay private and cannot be re-interpreted as anything but the image they are.
  for (const h of ["cache-control", "x-content-type-options", "content-security-policy", "content-disposition"]) {
    const v = res.headers.get(h);
    if (v && path[0] === "objects") out[h] = v;
  }
  return new NextResponse(bytes.byteLength ? bytes : null, { status: res.status, headers: out });
}

export { forward as GET, forward as POST, forward as PUT, forward as DELETE };
