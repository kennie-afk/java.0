"use client";

import { useActionState, type ReactNode } from "react";
import { Notice, buttonClass } from "@/components/ui";

export interface FormState {
  error: string | null;
  message: string | null;
}

export const INITIAL_FORM: FormState = { error: null, message: null };

/** A server-action form with its own pending state and result notice. */
export function ActionForm({
  action,
  submit,
  pending = "Working…",
  className,
  children,
  button
}: {
  action: (previous: FormState, form: FormData) => Promise<FormState>;
  submit: string;
  pending?: string;
  className?: string;
  children: ReactNode;
  button?: string;
}) {
  const [state, run, busy] = useActionState(action, INITIAL_FORM);
  return (
    <form action={run} className={className ?? "space-y-4"}>
      {children}
      {state.error ? <Notice tone="danger">{state.error}</Notice> : null}
      {state.message ? <Notice tone="good">{state.message}</Notice> : null}
      <button type="submit" disabled={busy} className={button ?? buttonClass}>
        {busy ? pending : submit}
      </button>
    </form>
  );
}
