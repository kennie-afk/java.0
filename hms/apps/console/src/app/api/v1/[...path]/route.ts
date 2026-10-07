import { cookies } from "next/headers";
import { NextResponse, type NextRequest } from "next/server";
import { clearSessionCookies, COOKIE, refreshTokens, setSessionCookies, type Tokens } from "@/lib/auth-server";

const API = process.env.HMS_API_URL ?? "http://localhost:8100";
const secure = process.env.HMS_COOKIE_SECURE === "true";

// Forwards the browser's call to the API with the token from the httpOnly cookie. Same-origin only:
// the browser never holds the token and the API needs no CORS for the console. When the access token has expired (its cookie is gone)
// or the API refuses it, the refresh token is used once, transparently, and the request is carried out with the new access token.
async function forward(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  const { path } = await context.params;
  const jar = await cookies();
  let token = jar.get(COOKIE.access)?.value;
  const refresh = jar.get(COOKIE.refresh)?.value;
  let fresh: Tokens | undefined;
  const signedOut = () => {
    const out = NextResponse.json({ code: "unauthorized", detail: "Sign in to continue." }, { status: 401 });
    clearSessionCookies(out.cookies);
    return out;
  };
  const renew = async (): Promise<NextResponse | null> => {
    if (!refresh) return signedOut();
    const r = await refreshTokens(API, refresh);
    if (r.ok) {
      fresh = r.tokens;
      token = r.tokens.token;
      return null;
    }
    // Could not refresh because the server is busy or unreachable: not the same as being signed out, so the cookies stay and the caller retries
    // (a queued offline write stays queued on any 5xx).
    return r.reason === "unavailable" ? NextResponse.json({ code: "unavailable", detail: "The server could not be reached. Try again." }, { status: 503 }) : signedOut();
  };
  if (!token) {
    const failed = await renew();
    if (failed) return failed;
  }
  const hasBody = request.method !== "GET" && request.method !== "HEAD";
  // A file upload is passed through byte for byte with its own boundary; everything else is JSON text. Read once so a retry can resend it.
  const upload = (request.headers.get("content-type") ?? "").startsWith("multipart/form-data");
  const body = hasBody ? (upload ? await request.arrayBuffer() : await request.text()) : undefined;
  const url = `${API}/v1/${path.join("/")}${request.nextUrl.search}`;
  const call = () => {
    const headers: Record<string, string> = { Authorization: `Bearer ${token}` };
    const reason = request.headers.get("x-access-reason");
    if (reason) headers["X-Access-Reason"] = reason;
    const idem = request.headers.get("idempotency-key");
    if (idem) headers["Idempotency-Key"] = idem;
    if (hasBody) headers["Content-Type"] = upload ? request.headers.get("content-type")! : "application/json";
    return fetch(url, { method: request.method, headers, body, cache: "no-store" });
  };
  let res = await call();
  if (res.status === 401 && refresh && !fresh) {
    // The API refused a token the cookie still held (sessions were ended, or the clock ran out first): one refresh, one retry. A 401 happens before
    // anything is executed, so resending a write is safe.
    const failed = await renew();
    if (failed) return failed;
    res = await call();
  }
  // Read as bytes, not text, so an image comes back intact.
  const bytes = await res.arrayBuffer();
  const out: Record<string, string> = { "Content-Type": res.headers.get("content-type") ?? "application/json" };
  if (res.headers.get("idempotent-replay")) out["Idempotent-Replay"] = "true";
  // Patient images stay private and cannot be re-interpreted as anything but the image they are.
  for (const h of ["cache-control", "x-content-type-options", "content-security-policy", "content-disposition"]) {
    const v = res.headers.get(h);
    if (v && path[0] === "objects") out[h] = v;
  }
  const response = new NextResponse(bytes.byteLength ? bytes : null, { status: res.status, headers: out });
  if (fresh) setSessionCookies(response.cookies, fresh, secure);
  return response;
}

export { forward as GET, forward as POST, forward as PUT, forward as DELETE };
