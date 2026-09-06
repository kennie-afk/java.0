import "server-only";
import { cookies } from "next/headers";

const COOKIE = "soko_session";

export interface Session {
  token: string;
  fullName: string;
  role: string;
  organisation?: string;
}

export async function readSession(): Promise<Session | null> {
  const store = await cookies();
  const raw = store.get(COOKIE)?.value;
  if (!raw) {
    return null;
  }
  try {
    const parsed = JSON.parse(Buffer.from(raw, "base64url").toString("utf8")) as Partial<Session>;
    if (!parsed.token || !parsed.fullName || !parsed.role) {
      return null;
    }
    return {
      token: parsed.token,
      fullName: parsed.fullName,
      role: parsed.role,
      organisation: parsed.organisation
    };
  } catch {
    return null;
  }
}

export async function writeSession(session: Session, maxAgeSeconds: number): Promise<void> {
  const store = await cookies();
  store.set(COOKIE, Buffer.from(JSON.stringify(session), "utf8").toString("base64url"), {
    httpOnly: true,
    sameSite: "lax",
    secure: process.env.NODE_ENV === "production",
    path: "/",
    maxAge: maxAgeSeconds
  });
}

export async function clearSession(): Promise<void> {
  const store = await cookies();
  store.delete(COOKIE);
}
