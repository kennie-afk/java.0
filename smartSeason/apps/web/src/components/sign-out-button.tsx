"use client";

import { useRouter } from "next/navigation";
import { useTransition } from "react";

export function SignOutButton() {
  const router = useRouter();
  const [pending, startTransition] = useTransition();

  async function signOut() {
    await fetch("/api/auth/logout", { method: "POST" });
    startTransition(() => {
      router.replace("/login");
      router.refresh();
    });
  }

  return (
    <button
      type="button"
      onClick={signOut}
      disabled={pending}
      className="flex w-full cursor-pointer flex-col items-center gap-1 rounded-lg px-1 py-2 text-[0.625rem] font-medium text-[var(--color-muted)] transition-colors hover:bg-[var(--color-raised)] hover:text-[var(--color-ink)] disabled:opacity-50"
    >
      <svg
        viewBox="0 0 24 24"
        className="h-[19px] w-[19px]"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.6"
        strokeLinecap="round"
        strokeLinejoin="round"
      >
        <path d="M15 17l5-5-5-5" />
        <path d="M20 12H9" />
        <path d="M12 20H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h6" />
      </svg>
      {pending ? "Wait…" : "Sign out"}
    </button>
  );
}
