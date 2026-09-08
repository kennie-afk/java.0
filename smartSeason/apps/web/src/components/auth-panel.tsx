"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { AccountSwitcher } from "@/components/account-switcher";
import { useState } from "react";

type Mode = "signin" | "register";

const FIELD =
  "w-full rounded-md border border-[var(--color-line)] bg-[var(--color-surface)] px-3 py-2 text-sm outline-none focus:border-[var(--color-accent)]";

export function AuthPanel({
  accounts = []
}: {
  /** Configured accounts for the switcher; empty when it is not enabled. */
  accounts?: { email: string; label: string }[];
}) {
  const router = useRouter();
  const [mode, setMode] = useState<Mode>("signin");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setBusy(true);

    const form = new FormData(event.currentTarget);

    if (mode === "register") {
      const password = String(form.get("password") ?? "");
      if (password !== String(form.get("confirmPassword") ?? "")) {
        setError("The two passwords do not match");
        setBusy(false);
        return;
      }
    }

    const endpoint = mode === "signin" ? "/api/auth/login" : "/api/auth/register";
    const payload =
      mode === "signin"
        ? {
            email: String(form.get("email") ?? ""),
            password: String(form.get("password") ?? "")
          }
        : {
            organisationName: String(form.get("organisationName") ?? ""),
            fullName: String(form.get("fullName") ?? ""),
            email: String(form.get("email") ?? ""),
            password: String(form.get("password") ?? ""),
            orgType: String(form.get("orgType") ?? "FARM")
          };

    try {
      const response = await fetch(endpoint, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
      });

      if (!response.ok) {
        const body = await response.json().catch(() => ({}));
        setError(body.message ?? "Something went wrong");
        setBusy(false);
        return;
      }

      router.replace("/");
      router.refresh();
    } catch {
      setError("The platform is not reachable right now");
      setBusy(false);
    }
  }

  return (
    <div className="rounded-lg border border-[var(--color-line)] bg-[var(--color-surface)] p-3.5">
      <div className="mb-5 flex gap-1 rounded-md bg-[#eef1ef] p-1">
        {(["signin", "register"] as Mode[]).map((value) => (
          <button
            key={value}
            type="button"
            onClick={() => {
              setMode(value);
              setError(null);
            }}
            className={`flex-1 rounded px-3 py-1.5 text-sm font-medium transition-colors ${
              mode === value
                ? "bg-[var(--color-surface)] text-[var(--color-ink)]"
                : "text-[var(--color-muted)]"
            }`}
          >
            {value === "signin" ? "Sign in" : "Create account"}
          </button>
        ))}
      </div>

      <form onSubmit={onSubmit} className="space-y-3">
        {mode === "register" ? (
          <>
            <label className="block">
              <span className="mb-1 block text-xs font-medium text-[var(--color-muted)]">
                Organisation
              </span>
              <input name="organisationName" required maxLength={255} className={FIELD} />
            </label>
            <label className="block">
              <span className="mb-1 block text-xs font-medium text-[var(--color-muted)]">
                Your name
              </span>
              <input name="fullName" required maxLength={255} className={FIELD} />
            </label>
            <label className="block">
              <span className="mb-1 block text-xs font-medium text-[var(--color-muted)]">Type</span>
              <select name="orgType" className={FIELD} defaultValue="FARM">
                <option value="FARM">Farm</option>
                <option value="COOPERATIVE">Cooperative</option>
                <option value="BUYER">Buyer</option>
                <option value="TRANSPORTER">Transporter</option>
              </select>
            </label>
          </>
        ) : null}

        <label className="block">
          <span className="mb-1 block text-xs font-medium text-[var(--color-muted)]">Email</span>
          <input name="email" type="email" required autoComplete="email" className={FIELD} />
        </label>

        <label className="block">
          <span className="mb-1 block text-xs font-medium text-[var(--color-muted)]">Password</span>
          <input
            name="password"
            type="password"
            required
            minLength={12}
            autoComplete={mode === "signin" ? "current-password" : "new-password"}
            className={FIELD}
          />
          {mode === "register" ? (
            <span className="mt-1 block text-xs text-[var(--color-muted)]">
              At least 12 characters.
            </span>
          ) : null}
        </label>

        {mode === "register" ? (
          <label className="block">
            <span className="mb-1 block text-xs font-medium text-[var(--color-muted)]">
              Confirm password
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
        ) : null}

        {mode === "signin" ? (
          <p className="text-right">
            <Link
              href="/forgot-password"
              className="text-xs text-[var(--color-muted)] underline-offset-2 hover:text-[var(--color-ink)] hover:underline"
            >
              Forgot your password?
            </Link>
          </p>
        ) : null}

        {error ? (
          <p role="alert" className="text-sm text-[var(--color-danger)]">
            {error}
          </p>
        ) : null}

        <button
          type="submit"
          disabled={busy}
          className="w-full rounded-md bg-[var(--color-ink)] px-3 py-2 text-sm font-medium text-white transition-opacity hover:bg-[#242832] disabled:opacity-50"
        >
          {busy ? "Working…" : mode === "signin" ? "Sign in" : "Create account"}
        </button>
      </form>

      {/* Only offered beside the sign-in form. Someone creating an account is
          registering a new organisation; switching to an existing one mid-way
          would throw away what they were doing. */}
      {mode === "signin" ? <AccountSwitcher accounts={accounts} /> : null}
    </div>
  );
}
