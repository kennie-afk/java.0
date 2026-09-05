import { NextResponse } from "next/server";
import { tokenCookieName } from "@/lib/session";

export async function POST() {
  const response = NextResponse.json({ ok: true });
  response.cookies.set(tokenCookieName, "", { httpOnly: true, path: "/", maxAge: 0 });
  return response;
}
