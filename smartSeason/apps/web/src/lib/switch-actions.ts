"use server";

import { cookies } from "next/headers";
import { redirect } from "next/navigation";
import { api, ApiError } from "@/lib/api";
import { refreshCookieName, tokenCookieName } from "@/lib/session";

interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

/**
 * Accounts the switcher may open, by address.
 *
 * An allowlist, not free text: this signs in without asking for a password, so
 * it must never reach an account somebody actually uses. The shared password is
 * read from the environment on the server and never sent to the browser.
 *
 * Enabled by QUICK_SIGN_IN, which defaults to off. It belongs on a build or
 * staging deployment, not one real people sign in to.
 */
const ACCOUNTS: Record<string, string> = {
  "demo@smartseason.local": "Administrator",
  "farmer@smartseason.local": "Farmer",
  "manager@smartseason.local": "Farm manager",
  "agronomist@smartseason.local": "Agronomist",
  "store@smartseason.local": "Storekeeper",
  "finance@smartseason.local": "Finance",
  "buyer@smartseason.local": "Buyer",
  "amina@smartseason.local": "Worker"
};

export async function accountSwitcherEnabled(): Promise<boolean> {
  return process.env.QUICK_SIGN_IN === "true" && Boolean(process.env.QUICK_SIGN_IN_PASSWORD);
}

export async function switchableAccounts(): Promise<{ email: string; label: string }[]> {
  if (!(await accountSwitcherEnabled())) return [];
  return Object.entries(ACCOUNTS).map(([email, label]) => ({ email, label }));
}

export async function signInAs(_prev: string | null, form: FormData): Promise<string | null> {
  if (!(await accountSwitcherEnabled())) {
    return "Account switching is not enabled on this deployment";
  }

  const email = String(form.get("email") ?? "");
  if (!ACCOUNTS[email]) {
    return "That account is not on the list";
  }

  let tokens: TokenResponse;
  try {
    tokens = await api.post<TokenResponse>("/api/identity/v1/auth/login", {
      email,
      password: process.env.QUICK_SIGN_IN_PASSWORD
    });
  } catch (error) {
    if (error instanceof ApiError) {
      return `Could not sign in as ${ACCOUNTS[email]}. The account may not exist yet.`;
    }
    return "The platform is not reachable right now";
  }

  const store = await cookies();
  const options = {
    httpOnly: true,
    sameSite: "lax" as const,
    secure: process.env.NODE_ENV === "production",
    path: "/"
  };
  store.set(tokenCookieName, tokens.accessToken, { ...options, maxAge: tokens.expiresIn });
  store.set(refreshCookieName, tokens.refreshToken, { ...options, maxAge: 60 * 60 * 24 * 14 });

  redirect("/");
}
