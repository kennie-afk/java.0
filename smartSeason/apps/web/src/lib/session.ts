import { cookies } from "next/headers";

const TOKEN_COOKIE = "ss_token";
const REFRESH_COOKIE = "ss_refresh";

export async function readToken(): Promise<string | null> {
  const store = await cookies();
  return store.get(TOKEN_COOKIE)?.value ?? null;
}

/**
 * The tenant (organisation) the signed-in user belongs to, read from the `tid`
 * claim the identity service puts in the access token. Services derive tenancy
 * from the same claim, so this is only ever used to prefill request bodies that
 * name the caller's own organisation — never to widen what the caller can see.
 */
export function tenantIdFromToken(token: string | null): string | null {
  if (!token) return null;
  const payload = token.split(".")[1];
  if (!payload) return null;
  try {
    const json = Buffer.from(payload.replace(/-/g, "+").replace(/_/g, "/"), "base64").toString("utf8");
    const claims = JSON.parse(json) as { tid?: string };
    return claims.tid ?? null;
  } catch {
    return null;
  }
}

export async function readTenantId(): Promise<string | null> {
  return tenantIdFromToken(await readToken());
}

/** Roles from the token's `roles` claim, which the identity service signs. */
export function rolesFromToken(token: string | null): string[] {
  if (!token) return [];
  const payload = token.split(".")[1];
  if (!payload) return [];
  try {
    const json = Buffer.from(payload.replace(/-/g, "+").replace(/_/g, "/"), "base64").toString("utf8");
    const claims = JSON.parse(json) as { roles?: string };
    return (claims.roles ?? "")
      .split(/[,\s]+/)
      .map((role) => role.trim().toUpperCase())
      .filter(Boolean);
  } catch {
    return [];
  }
}

export async function readRoles(): Promise<string[]> {
  return rolesFromToken(await readToken());
}

/** The signed-in user's id, from the token's subject. */
export function userIdFromToken(token: string | null): string | null {
  if (!token) return null;
  const payload = token.split(".")[1];
  if (!payload) return null;
  try {
    const json = Buffer.from(payload.replace(/-/g, "+").replace(/_/g, "/"), "base64").toString("utf8");
    return (JSON.parse(json) as { sub?: string }).sub ?? null;
  } catch {
    return null;
  }
}

export async function readUserId(): Promise<string | null> {
  return userIdFromToken(await readToken());
}

export async function readRefreshToken(): Promise<string | null> {
  const store = await cookies();
  return store.get(REFRESH_COOKIE)?.value ?? null;
}

/** Seconds until the token expires; 0 when it is already expired or unreadable. */
export function secondsUntilExpiry(token: string | null): number {
  if (!token) return 0;
  const payload = token.split(".")[1];
  if (!payload) return 0;
  try {
    const json = Buffer.from(payload.replace(/-/g, "+").replace(/_/g, "/"), "base64").toString("utf8");
    const { exp } = JSON.parse(json) as { exp?: number };
    if (!exp) return 0;
    return Math.max(0, exp - Math.floor(Date.now() / 1000));
  } catch {
    return 0;
  }
}

export const tokenCookieName = TOKEN_COOKIE;
export const refreshCookieName = REFRESH_COOKIE;
