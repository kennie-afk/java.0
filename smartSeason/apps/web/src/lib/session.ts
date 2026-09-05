import { cookies } from "next/headers";

const TOKEN_COOKIE = "ss_token";

export async function readToken(): Promise<string | null> {
  const store = await cookies();
  return store.get(TOKEN_COOKIE)?.value ?? null;
}

export const tokenCookieName = TOKEN_COOKIE;
