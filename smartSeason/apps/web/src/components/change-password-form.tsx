"use client";

import { useState } from "react";
import { bareInputClass, buttonClass } from "@/components/ui";

export function ChangePasswordForm() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState<string | null>(null);

  async function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    setDone(null);

    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const newPassword = String(form.get("newPassword") ?? "");

    if (newPassword !== String(form.get("confirmPassword") ?? "")) {
      setError("The two new passwords do not match");
      setBusy(false);
      return;
    }

    try {
      const response = await fetch("/api/auth/password", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          action: "change",
          currentPassword: String(form.get("currentPassword") ?? ""),
          newPassword
        })
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) {
        setError(body.message ?? "That request was rejected");
        setBusy(false);
        return;
      }
      setDone(body.message ?? "Your password has been changed.");
      formElement.reset();
      setBusy(false);
    } catch {
      setError("The platform is not reachable right now");
      setBusy(false);
    }
  }

  const label = "mb-1 block text-2xs font-medium uppercase tracking-[0.06em] text-[var(--color-faint)]";

  return (
    <form
      onSubmit={onSubmit}
      className="max-w-md space-y-3 rounded-lg border border-[var(--color-line)] bg-[var(--color-surface)] p-3.5"
    >
      <label className="block">
        <span className={label}>Current password</span>
        <input
          name="currentPassword"
          type="password"
          required
          autoComplete="current-password"
          className={bareInputClass}
        />
      </label>
      <label className="block">
        <span className={label}>New password</span>
        <input
          name="newPassword"
          type="password"
          required
          minLength={12}
          autoComplete="new-password"
          className={bareInputClass}
        />
        <span className="mt-1 block text-2xs text-[var(--color-muted)]">At least 12 characters.</span>
      </label>
      <label className="block">
        <span className={label}>Confirm new password</span>
        <input
          name="confirmPassword"
          type="password"
          required
          minLength={12}
          autoComplete="new-password"
          className={bareInputClass}
        />
      </label>

      {error ? (
        <p role="alert" className="text-2xs text-[var(--color-danger)]">
          {error}
        </p>
      ) : null}
      {done ? <p className="text-2xs text-[var(--color-good)]">{done}</p> : null}

      <button type="submit" disabled={busy} className={buttonClass}>
        {busy ? "Saving…" : "Change password"}
      </button>
    </form>
  );
}
