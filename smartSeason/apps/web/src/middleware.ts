import { NextResponse, type NextRequest } from "next/server";

const TOKEN_COOKIE = "ss_token";
const REFRESH_COOKIE = "ss_refresh";

// Rotate a little before the access token actually lapses, so a request never
// races the expiry and comes back as a 401 mid-render.
const REFRESH_WHEN_UNDER_SECONDS = 120;

const GATEWAY = process.env.GATEWAY_URL ?? "http://localhost:8080";

function secondsLeft(token: string): number {
  const payload = token.split(".")[1];
  if (!payload) return 0;
  try {
    const json = atob(payload.replace(/-/g, "+").replace(/_/g, "/"));
    const { exp } = JSON.parse(json) as { exp?: number };
    if (!exp) return 0;
    return exp - Math.floor(Date.now() / 1000);
  } catch {
    return 0;
  }
}

function toLogin(request: NextRequest): NextResponse {
  const response = NextResponse.redirect(new URL("/login", request.url));
  response.cookies.set(TOKEN_COOKIE, "", { httpOnly: true, path: "/", maxAge: 0 });
  response.cookies.set(REFRESH_COOKIE, "", { httpOnly: true, path: "/", maxAge: 0 });
  return response;
}

export async function middleware(request: NextRequest) {
  const token = request.cookies.get(TOKEN_COOKIE)?.value;
  const refreshToken = request.cookies.get(REFRESH_COOKIE)?.value;

  if (token && secondsLeft(token) > REFRESH_WHEN_UNDER_SECONDS) {
    return NextResponse.next();
  }

  // Without a refresh token there is nothing to rotate, so an expired or
  // missing access token means the session is genuinely over.
  if (!refreshToken) {
    return token ? toLogin(request) : NextResponse.next();
  }

  let rotated: { accessToken: string; refreshToken: string; expiresIn: number };
  try {
    const response = await fetch(`${GATEWAY}/api/identity/v1/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json", Accept: "application/json" },
      body: JSON.stringify({ refreshToken }),
      cache: "no-store"
    });
    if (!response.ok) {
      return toLogin(request);
    }
    rotated = await response.json();
  } catch {
    // identity-service is unreachable. Let the request through so the page can
    // report the outage itself rather than bouncing the user to a sign-in form
    // that would fail for the same reason.
    return NextResponse.next();
  }

  // The page renders in this same pass, so it has to see the rotated token
  // rather than the stale cookie the browser sent.
  request.cookies.set(TOKEN_COOKIE, rotated.accessToken);
  request.cookies.set(REFRESH_COOKIE, rotated.refreshToken);

  const response = NextResponse.next({ request });
  const options = {
    httpOnly: true,
    sameSite: "lax" as const,
    secure: process.env.NODE_ENV === "production",
    path: "/"
  };
  response.cookies.set(TOKEN_COOKIE, rotated.accessToken, {
    ...options,
    maxAge: rotated.expiresIn
  });
  response.cookies.set(REFRESH_COOKIE, rotated.refreshToken, {
    ...options,
    maxAge: 60 * 60 * 24 * 14
  });
  return response;
}

export const config = {
  matcher: ["/((?!api|_next/static|_next/image|favicon.ico|.*\\.svg).*)"]
};
