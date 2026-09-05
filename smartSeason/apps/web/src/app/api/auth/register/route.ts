import { NextResponse } from "next/server";
import { api, ApiError } from "@/lib/api";
import { tokenCookieName } from "@/lib/session";

interface TokenResponse {
  accessToken: string;
  expiresIn: number;
  userId: string;
  organisationId: string;
  roles: string;
}

export async function POST(request: Request) {
  let payload: Record<string, string>;
  try {
    payload = await request.json();
  } catch {
    return NextResponse.json({ message: "Malformed request body" }, { status: 400 });
  }

  try {
    const tokens = await api.post<TokenResponse>("/api/identity/v1/auth/register", payload);

    const response = NextResponse.json({ organisationId: tokens.organisationId });
    response.cookies.set(tokenCookieName, tokens.accessToken, {
      httpOnly: true,
      sameSite: "lax",
      secure: process.env.NODE_ENV === "production",
      path: "/",
      maxAge: tokens.expiresIn
    });
    return response;
  } catch (error) {
    if (error instanceof ApiError) {
      return NextResponse.json(
        { message: error.problem?.detail ?? "Registration failed" },
        { status: error.status }
      );
    }
    return NextResponse.json(
      { message: "The platform is not reachable right now" },
      { status: 503 }
    );
  }
}
