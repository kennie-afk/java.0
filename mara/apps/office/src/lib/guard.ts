import { cookies } from "next/headers";
import { COOKIE, open, type Session } from "./session";

/** Same-origin check for state-changing calls: a custom header a cross-site form cannot set, and a matching Origin. */
export function sameOrigin(request: Request): boolean {
  if (request.headers.get("x-office-csrf") !== "1") return false;
  const origin = request.headers.get("origin");
  if (!origin) return true;
  try {
    return new URL(origin).host === request.headers.get("host");
  } catch {
    return false;
  }
}

export async function currentSession(): Promise<Session | null> {
  const jar = await cookies();
  return open(jar.get(COOKIE)?.value);
}

export function secureCookie(request: Request): boolean {
  return process.env.OFFICE_COOKIE_SECURE === "true" || request.headers.get("x-forwarded-proto") === "https";
}

export const bases = () => ({
  identity: process.env.IDENTITY_BASE_URL,
  core: process.env.CORE_BASE_URL,
  sync: process.env.SYNC_BASE_URL
});
