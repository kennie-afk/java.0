"use client";

import { useRouter } from "next/navigation";
import { useTransition } from "react";
import { Icon } from "@/components/icons";

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
      className="flex w-full cursor-pointer items-center gap-2.5 px-3 py-2 text-sm text-red-600 hover:bg-red-50 disabled:opacity-50 dark:text-red-400 dark:hover:bg-red-500/10"
    >
      <Icon name="logout" className="h-3.5 w-3.5" />
      {pending ? "Signing out…" : "Sign out"}
    </button>
  );
}
