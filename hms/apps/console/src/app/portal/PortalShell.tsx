"use client";

import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState } from "react";

export function PortalShell({ children }: { children: React.ReactNode }) {
  const path = usePathname();
  const router = useRouter();
  const [name, setName] = useState<string | null>(null);
  const open = path.startsWith("/portal/login") || path.startsWith("/portal/activate");
  useEffect(() => {
    if (open) return;
    fetch("/api/portal-session").then(async (r) => (r.ok ? setName((await r.json()).patientName) : router.replace("/portal/login")));
  }, [open, router]);
  return (
    <div className="mx-auto min-h-screen max-w-3xl px-4 pb-10">
      <header className="flex items-center justify-between border-b border-line py-3">
        <div className="font-display text-lg font-semibold">Patient portal</div>
        {!open && name !== null && (
          <div className="flex items-center gap-3 text-sm">
            <span className="text-muted">{name}</span>
            <button className="cursor-pointer underline" onClick={async () => { await fetch("/api/portal-session", { method: "DELETE" }); router.replace("/portal/login"); }}>Sign out</button>
          </div>
        )}
      </header>
      <main className="space-y-4 pt-4">{children}</main>
    </div>
  );
}
