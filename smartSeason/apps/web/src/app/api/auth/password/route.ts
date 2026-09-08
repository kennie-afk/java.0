import { NextResponse } from "next/server";
import { api, ApiError } from "@/lib/api";
import { readToken } from "@/lib/session";

/**
 * Proxies the password endpoints so the browser never talks to the gateway
 * directly and never sees the access token, which is httpOnly.
 */
const PUBLIC_ROUTES: Record<string, string> = {
  forgot: "/api/identity/v1/auth/forgot-password",
  reset: "/api/identity/v1/auth/reset-password"
};

const CHANGE = "/api/identity/v1/account/change-password";

export async function POST(request: Request) {
  let payload: { action?: string; [key: string]: unknown };
  try {
    payload = await request.json();
  } catch {
    return NextResponse.json({ message: "Malformed request body" }, { status: 400 });
  }

  const action = String(payload.action ?? "");
  const { action: _ignored, ...body } = payload;
  void _ignored;

  const path = action === "change" ? CHANGE : PUBLIC_ROUTES[action];
  if (!path) {
    return NextResponse.json({ message: "Unknown action" }, { status: 400 });
  }

  // Changing a password is the only one of the three that needs a session.
  const token = action === "change" ? await readToken() : null;
  if (action === "change" && !token) {
    return NextResponse.json({ message: "Your session has expired" }, { status: 401 });
  }

  try {
    const result = await api.post<Record<string, unknown>>(path, body, token);
    return NextResponse.json(result ?? {});
  } catch (error) {
    if (error instanceof ApiError) {
      return NextResponse.json(
        { message: error.problem?.detail ?? "That request was rejected" },
        { status: error.status }
      );
    }
    return NextResponse.json(
      { message: "The platform is not reachable right now" },
      { status: 503 }
    );
  }
}
