"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";

const FIELD =
  "w-full rounded-md border border-[var(--color-line)] bg-[var(--color-surface)] px-3 py-2 text-sm outline-none focus:border-[var(--color-accent)]";

export function ForgotPasswordForm() {
  const router = useRouter();
  const [sent, setSent] = useState(false);
  const [devCode, setDevCode] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    const email = String(new FormData(event.currentTarget).get("email") ?? "");

    try {
      const response = await fetch("/api/auth/password", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ action: "forgot", email })
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) {
        setError(body.message ?? "That request was rejected");
        setBusy(false);
        return;
      }
      setSent(true);
      // Present only while the service is configured to expose it, so the flow
      // can be walked through without a mail or SMS provider wired up.
      setDevCode(body.devCode ?? null);
      setBusy(false);
    } catch {
      setError("The platform is not reachable right now");
      setBusy(false);
    }
  }

  if (sent) {
    return (
      <div className="rounded-lg border border-[var(--color-line)] bg-[var(--color-surface)] p-3.5">
        <p className="text-xs leading-relaxed">
          If that address has an account, a reset code has been sent to it. The code is good
          for 15 minutes.
        </p>
        {devCode ? (
          <p className="mt-2 rounded-md bg-[var(--color-warn-soft)] px-2.5 py-2 text-xs text-[var(--color-warn)]">
            Development mode: your code is <strong className="tabular-nums">{devCode}</strong>.
            This is shown because no mail provider is configured.
          </p>
        ) : null}
        <button
          type="button"
          onClick={() => router.push("/reset-password")}
          className="mt-3 w-full rounded-md bg-[var(--color-ink)] px-3 py-2 text-sm font-medium text-white transition-colors hover:bg-[#242832]"
        >
          Enter the code
        </button>
      </div>
    );
  }

  return (
    <form
      onSubmit={onSubmit}
      className="space-y-3 rounded-lg border border-[var(--color-line)] bg-[var(--color-surface)] p-3.5"
    >
      <label className="block">
        <span className="mb-1 block text-xs font-medium text-[var(--color-muted)]">Email</span>
        <input name="email" type="email" required autoComplete="email" className={FIELD} />
      </label>
      {error ? (
        <p role="alert" className="text-xs text-[var(--color-danger)]">
          {error}
        </p>
      ) : null}
      <button
        type="submit"
        disabled={busy}
        className="w-full rounded-md bg-[var(--color-ink)] px-3 py-2 text-sm font-medium text-white transition-colors hover:bg-[#242832] disabled:opacity-50"
      >
        {busy ? "Sending…" : "Send a reset code"}
      </button>
      <p className="text-center">
        <Link
          href="/login"
          className="text-xs text-[var(--color-muted)] underline-offset-2 hover:text-[var(--color-ink)] hover:underline"
        >
          Back to sign in
        </Link>
      </p>
    </form>
  );
}
