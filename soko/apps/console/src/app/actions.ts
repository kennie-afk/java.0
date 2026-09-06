"use server";

import { redirect } from "next/navigation";
import { api, describeError } from "@/lib/api";
import { clearSession, writeSession } from "@/lib/session";

export interface AuthState {
  error: string | null;
  message: string | null;
}

export async function signIn(_previous: AuthState, form: FormData): Promise<AuthState> {
  const email = String(form.get("email") ?? "").trim();
  const password = String(form.get("password") ?? "");

  if (!email || !password) {
    return { error: "Enter both an email address and a password.", message: null };
  }

  try {
    const result = await api.login(email, password);
    await writeSession(
      {
        token: result.accessToken,
        fullName: result.fullName,
        role: result.role,
        organisation: result.organisation
      },
      result.expiresInSeconds
    );
  } catch (caught) {
    return { error: describeError(caught), message: null };
  }

  redirect("/");
}

export async function signUp(_previous: AuthState, form: FormData): Promise<AuthState> {
  const organisationName = String(form.get("organisationName") ?? "").trim();
  const fullName = String(form.get("fullName") ?? "").trim();
  const email = String(form.get("email") ?? "").trim();
  const password = String(form.get("password") ?? "");

  if (!organisationName || !fullName || !email || !password) {
    return { error: "Every field is needed to open an account.", message: null };
  }
  if (password.length < 10) {
    return { error: "Use a password of at least 10 characters.", message: null };
  }

  try {
    const result = await api.register({ organisationName, fullName, email, password });
    await writeSession(
      {
        token: result.accessToken,
        fullName: result.fullName,
        role: result.role,
        organisation: result.organisation
      },
      result.expiresInSeconds
    );
  } catch (caught) {
    return { error: describeError(caught), message: null };
  }

  redirect("/");
}

export async function requestReset(_previous: AuthState, form: FormData): Promise<AuthState> {
  const email = String(form.get("email") ?? "").trim();
  if (!email) {
    return { error: "Enter the email address on the account.", message: null };
  }
  try {
    await api.forgot(email);
  } catch (caught) {
    return { error: describeError(caught), message: null };
  }
  return {
    error: null,
    message: "If that email has an account, a reset link is on its way."
  };
}

export async function signOut(): Promise<void> {
  await clearSession();
  redirect("/login");
}
