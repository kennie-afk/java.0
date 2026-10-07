"use client";

import Link from "next/link";
import { useActionState } from "react";
import { resetPassword, type AuthState } from "@/app/actions";
import { Logo } from "@/components/logo";
import { Field, Notice, buttonClass, inputClass } from "@/components/ui";

const INITIAL: AuthState = { error: null, message: null };

export function ResetForm({ token }: { token: string }) {
  const [state, action, working] = useActionState(resetPassword, INITIAL);
  const done = Boolean(state.message);
  return (
    <div className="w-full rounded-2xl border border-[var(--color-line)] bg-[var(--color-surface)] p-7">
      <div className="mb-6 flex items-center gap-2.5">
        <Logo className="h-8 w-8" />
        <span className="text-[1.25rem] font-semibold tracking-[-0.01em]">FreshFerm</span>
      </div>
      <h1 className="text-[1.25rem] font-semibold tracking-[-0.01em]">Choose a new password</h1>
      <p className="mt-1 mb-5 text-[0.958rem] text-[var(--color-muted)]">
        The link works once and expires 30 minutes after it was sent.
      </p>
      {state.error ? <div className="mb-4"><Notice tone="danger">{state.error}</Notice></div> : null}
      {state.message ? <div className="mb-4"><Notice tone="good">{state.message}</Notice></div> : null}
      {done ? (
        <Link href="/login" className={`${buttonClass} w-full justify-center`}>Go to sign in</Link>
      ) : (
        <form action={action} className="flex flex-col gap-4">
          <input type="hidden" name="token" value={token} />
          <Field label="New password" hint="At least 10 characters.">
            <input name="password" type="password" autoComplete="new-password" minLength={10} className={inputClass} required />
          </Field>
          <Field label="Repeat the password">
            <input name="confirm" type="password" autoComplete="new-password" minLength={10} className={inputClass} required />
          </Field>
          <button type="submit" className={`${buttonClass} mt-1 w-full justify-center`} disabled={working || !token}>
            {working ? "Saving…" : "Change password"}
          </button>
          {!token ? <p className="text-[0.875rem] text-[var(--color-danger)]">Open the link from the email again; this page has no token.</p> : null}
        </form>
      )}
    </div>
  );
}
