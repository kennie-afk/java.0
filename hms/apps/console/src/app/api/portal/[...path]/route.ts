import { cookies } from "next/headers";
import { NextResponse, type NextRequest } from "next/server";

const API = process.env.HMS_API_URL ?? "http://localhost:8100";

// Forwards a patient's call to the portal API with the portal token from its httpOnly cookie. It never touches /v1.
async function forward(request: NextRequest, context: { params: Promise<{ path: string[] }> }) {
  const { path } = await context.params;
  const token = (await cookies()).get("hms_portal")?.value;
  if (!token) return NextResponse.json({ code: "unauthorized", detail: "Sign in to continue." }, { status: 401 });
  const hasBody = request.method !== "GET" && request.method !== "HEAD";
  const headers: Record<string, string> = { Authorization: `Bearer ${token}` };
  if (hasBody) headers["Content-Type"] = "application/json";
  const res = await fetch(`${API}/portal/${path.join("/")}${request.nextUrl.search}`, { method: request.method, headers, body: hasBody ? await request.text() : undefined, cache: "no-store" });
  const text = await res.text();
  return new NextResponse(text.length ? text : null, { status: res.status, headers: { "Content-Type": res.headers.get("content-type") ?? "application/json" } });
}

export { forward as GET, forward as POST };
export const dynamic = "force-dynamic";
