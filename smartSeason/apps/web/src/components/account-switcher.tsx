"use client";

import { useActionState } from "react";
import { signInAs } from "@/lib/switch-actions";
import { bareSelectClass, secondaryButtonClass } from "@/components/ui";

/**
 * Opens the platform as one of the configured accounts, so the effect of each
 * role can be seen without keeping eight passwords to hand. Rendered only where
 * QUICK_SIGN_IN is switched on.
 */
export function AccountSwitcher({ accounts }: { accounts: { email: string; label: string }[] }) {
  const [error, action, pending] = useActionState(signInAs, null);

  if (accounts.length === 0) return null;

  return (
    <div className="mt-4 border-t border-[var(--color-line)] pt-3">
      <p className="mb-2 text-2xs font-medium uppercase tracking-[0.06em] text-[var(--color-faint)]">
        Sign in as
      </p>
      <form action={action} className="flex items-center gap-2">
        <select name="email" defaultValue={accounts[0].email} className={bareSelectClass}>
          {accounts.map((account) => (
            <option key={account.email} value={account.email}>
              {account.label}
            </option>
          ))}
        </select>
        <button type="submit" disabled={pending} className={`${secondaryButtonClass} shrink-0`}>
          {pending ? "Signing in…" : "Go"}
        </button>
      </form>
      {error ? (
        <p role="alert" className="mt-2 text-2xs text-[var(--color-danger)]">
          {error}
        </p>
      ) : null}
      <p className="mt-2 text-2xs leading-relaxed text-[var(--color-muted)]">
        The same screens change with the role: a farm manager cannot alter wage rates, a
        farmer can read the ledger but not post to it, a worker sees only their own tasks.
      </p>
    </div>
  );
}
