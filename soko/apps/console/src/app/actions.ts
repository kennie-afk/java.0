"use server";

import { redirect } from "next/navigation";
import { api, describeError } from "@/lib/api";
import { homeFor } from "@/lib/home";
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

  let destination = "/";
  try {
    const result = await api.login(email, password);
    destination = homeFor(result.role);
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

  redirect(destination);
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
  return { error: null, message: "If that email has an account, a reset link is on its way." };
}

export async function resetPassword(_previous: AuthState, form: FormData): Promise<AuthState> {
  const token = String(form.get("token") ?? "");
  const password = String(form.get("password") ?? "");
  const confirm = String(form.get("confirm") ?? "");
  if (!token) {
    return { error: "This link is missing its token. Open the link from the email again.", message: null };
  }
  if (password.length < 10) {
    return { error: "Use a password of at least 10 characters.", message: null };
  }
  if (password !== confirm) {
    return { error: "The two passwords do not match.", message: null };
  }
  try {
    await api.reset(token, password);
  } catch (caught) {
    return { error: describeError(caught), message: null };
  }
  return { error: null, message: "Your password has been changed. Sign in with the new one." };
}

export async function signOut(): Promise<void> {
  await clearSession();
  redirect("/login");
}
