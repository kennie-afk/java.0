"use client";

import { useActionState, useState } from "react";
import { Logo } from "@/components/logo";
import { requestReset, signIn, signUp, type AuthState } from "@/app/actions";
import { Field, Notice, buttonClass, inputClass } from "@/components/ui";

const INITIAL: AuthState = { error: null, message: null };

type Mode = "signin" | "signup" | "reset";

export function AuthPanel() {
  const [mode, setMode] = useState<Mode>("signin");
  const [signInState, signInAction, signingIn] = useActionState(signIn, INITIAL);
  const [signUpState, signUpAction, signingUp] = useActionState(signUp, INITIAL);
  const [resetState, resetAction, resetting] = useActionState(requestReset, INITIAL);

  const state = mode === "signin" ? signInState : mode === "signup" ? signUpState : resetState;

  return (
    <div className="rounded-2xl border border-[var(--color-line)] bg-[var(--color-surface)] p-7 shadow-[0_1px_2px_rgba(26,28,24,0.04)]">
      <div className="mb-6 flex items-center gap-2.5">
        <Logo className="h-8 w-8" />
        <span className="text-[1.0625rem] font-semibold tracking-[-0.01em]">Soko</span>
      </div>

      {mode !== "reset" ? (
        <div className="mb-6 flex rounded-lg bg-[var(--color-raised)] p-1">
          <button type="button" onClick={() => setMode("signin")}
            className={`flex-1 rounded-md px-3 py-1.5 text-[0.8125rem] font-medium transition-colors ${
              mode === "signin" ? "bg-[var(--color-surface)] text-[var(--color-ink)] shadow-sm" : "text-[var(--color-muted)]"
            }`}>
            Sign in
          </button>
          <button type="button" onClick={() => setMode("signup")}
            className={`flex-1 rounded-md px-3 py-1.5 text-[0.8125rem] font-medium transition-colors ${
              mode === "signup" ? "bg-[var(--color-surface)] text-[var(--color-ink)] shadow-sm" : "text-[var(--color-muted)]"
            }`}>
            Create account
          </button>
        </div>
      ) : (
        <div className="mb-6">
          <h2 className="text-[1.0625rem] font-semibold tracking-[-0.01em]">Reset your password</h2>
          <p className="mt-1 text-[0.8125rem] text-[var(--color-muted)]">
            We will send a link to the address on the account.
          </p>
        </div>
      )}

      {state.error ? <div className="mb-4"><Notice tone="danger">{state.error}</Notice></div> : null}
      {state.message ? <div className="mb-4"><Notice tone="good">{state.message}</Notice></div> : null}

      {mode === "signin" ? (
        <form action={signInAction} className="flex flex-col gap-4">
          <Field label="Email address">
            <input name="email" type="email" autoComplete="username"
              placeholder="grace@mazingira.co.ke" className={inputClass} required />
          </Field>
          <Field label="Password">
            <input name="password" type="password" autoComplete="current-password"
              placeholder="••••••••" className={inputClass} required />
          </Field>
          <button type="submit" className={`${buttonClass} mt-1 w-full justify-center`} disabled={signingIn}>
            {signingIn ? "Signing in…" : "Sign in"}
          </button>
          <button type="button" onClick={() => setMode("reset")}
            className="text-[0.8125rem] text-[var(--color-muted)] underline-offset-2 hover:text-[var(--color-ink)] hover:underline">
            Forgot your password?
          </button>
        </form>
      ) : mode === "signup" ? (
        <form action={signUpAction} className="flex flex-col gap-4">
          <Field label="Business name">
            <input name="organisationName" placeholder="Mazingira Fresh Distributors"
              className={inputClass} required />
          </Field>
          <Field label="Your name">
            <input name="fullName" autoComplete="name" placeholder="Grace Wanjiku"
              className={inputClass} required />
          </Field>
          <Field label="Email address">
            <input name="email" type="email" autoComplete="username"
              placeholder="grace@mazingira.co.ke" className={inputClass} required />
          </Field>
          <Field label="Password" hint="At least 10 characters.">
            <input name="password" type="password" autoComplete="new-password"
              placeholder="••••••••••" className={inputClass} minLength={10} required />
          </Field>
          <button type="submit" className={`${buttonClass} mt-1 w-full justify-center`} disabled={signingUp}>
            {signingUp ? "Creating…" : "Create account"}
          </button>
          <p className="text-[0.75rem] leading-relaxed text-[var(--color-faint)]">
            Your business gets its own tenant. Suppliers, catalogue and orders stay private to it.
          </p>
        </form>
      ) : (
        <form action={resetAction} className="flex flex-col gap-4">
          <Field label="Email address">
            <input name="email" type="email" autoComplete="username"
              placeholder="grace@mazingira.co.ke" className={inputClass} required />
          </Field>
          <button type="submit" className={`${buttonClass} mt-1 w-full justify-center`} disabled={resetting}>
            {resetting ? "Sending…" : "Send reset link"}
          </button>
          <button type="button" onClick={() => setMode("signin")}
            className="text-[0.8125rem] text-[var(--color-muted)] underline-offset-2 hover:text-[var(--color-ink)] hover:underline">
            Back to sign in
          </button>
        </form>
      )}
    </div>
  );
}
