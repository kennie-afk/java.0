"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";

const FIELD =
  "w-full rounded-md border border-[var(--color-line)] bg-[var(--color-surface)] px-3 py-2 text-sm outline-none focus:border-[var(--color-accent)]";

export function ResetPasswordForm() {
  const router = useRouter();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  async function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError(null);

    const form = new FormData(event.currentTarget);
    const newPassword = String(form.get("newPassword") ?? "");
    if (newPassword !== String(form.get("confirmPassword") ?? "")) {
      setError("The two passwords do not match");
      setBusy(false);
      return;
    }

    try {
      const response = await fetch("/api/auth/password", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          action: "reset",
          email: String(form.get("email") ?? ""),
          code: String(form.get("code") ?? ""),
          newPassword
        })
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) {
        setError(body.message ?? "That reset code is not valid");
        setBusy(false);
        return;
      }
      setDone(true);
      setBusy(false);
    } catch {
      setError("The platform is not reachable right now");
      setBusy(false);
    }
  }

  if (done) {
    return (
      <div className="rounded-lg border border-[var(--color-line)] bg-[var(--color-surface)] p-3.5">
        <p className="text-xs leading-relaxed">
          Your password has been reset, and every other session has been signed out. Sign in
          with the new password.
        </p>
        <button
          type="button"
          onClick={() => router.push("/login")}
          className="mt-3 w-full rounded-md bg-[var(--color-ink)] px-3 py-2 text-sm font-medium text-white transition-colors hover:bg-[#242832]"
        >
          Go to sign in
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
      <label className="block">
        <span className="mb-1 block text-xs font-medium text-[var(--color-muted)]">
          Reset code
        </span>
        <input
          name="code"
          required
          inputMode="numeric"
          autoComplete="one-time-code"
          className={`${FIELD} tabular-nums tracking-[0.2em]`}
        />
      </label>
      <label className="block">
        <span className="mb-1 block text-xs font-medium text-[var(--color-muted)]">
          New password
        </span>
        <input
          name="newPassword"
          type="password"
          required
          minLength={12}
          autoComplete="new-password"
          className={FIELD}
        />
        <span className="mt-1 block text-xs text-[var(--color-muted)]">At least 12 characters.</span>
      </label>
      <label className="block">
        <span className="mb-1 block text-xs font-medium text-[var(--color-muted)]">
          Confirm new password
        </span>
        <input
          name="confirmPassword"
          type="password"
          required
          minLength={12}
          autoComplete="new-password"
          className={FIELD}
        />
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
        {busy ? "Resetting…" : "Set the new password"}
      </button>
      <p className="text-center">
        <Link
          href="/forgot-password"
          className="text-xs text-[var(--color-muted)] underline-offset-2 hover:text-[var(--color-ink)] hover:underline"
        >
          Request another code
        </Link>
      </p>
    </form>
  );
}
