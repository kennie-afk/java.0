"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { type ReactNode, useEffect, useState } from "react";
import { Logo } from "./logo";

const LINKS = [
  { href: "/", label: "Today" },
  { href: "/sales", label: "Sales" },
  { href: "/tills", label: "Tills" },
  { href: "/staff", label: "Staff" },
  { href: "/exceptions", label: "Exceptions" },
  { href: "/audit", label: "Audit trail" }
];

const active = (path: string, href: string) => (href === "/" ? path === "/" : path === href || path.startsWith(href + "/"));

export function Shell({ children }: { children: ReactNode }) {
  const path = usePathname();
  const [signedIn, setSignedIn] = useState<boolean | null>(null);
  const [tenant, setTenant] = useState<string | null>(null);

  useEffect(() => {
    if (path.startsWith("/signin")) return;
    fetch("/api/session", { cache: "no-store" }).then(async (r) => {
      if (r.ok) {
        const j = await r.json();
        setTenant(j.tenant ?? null);
        setSignedIn(true);
      } else {
        location.assign("/signin");
      }
    });
  }, [path]);

  if (path.startsWith("/signin")) return <main className="mx-auto max-w-sm p-4 pt-16">{children}</main>;
  if (signedIn === null) return <main className="p-4 text-xs text-muted">Loading…</main>;

  async function signOut() {
    await fetch("/api/session", { method: "DELETE", headers: { "x-office-csrf": "1" } });
    location.assign("/signin");
  }

  return (
    <div className="flex min-h-screen flex-col md:flex-row">
      <aside className="no-print flex shrink-0 flex-col gap-3 border-b border-line bg-surface p-3 md:w-48 md:border-b-0 md:border-r">
        <Link href="/" aria-label="Mara back office home"><Logo /></Link>
        <div className="text-2xs font-semibold uppercase tracking-wider text-muted">Back office</div>
        <nav aria-label="Primary" className="flex flex-row flex-wrap gap-1 md:flex-col">
          {LINKS.map((l) => (
            <Link key={l.href} href={l.href} className="navlink" aria-current={active(path, l.href) ? "page" : undefined}>{l.label}</Link>
          ))}
        </nav>
        <div className="mt-auto space-y-1 border-t border-line pt-2 text-2xs text-muted">
          {tenant ? <div className="break-all">Tenant {tenant}</div> : null}
          <button type="button" className="navlink w-full" onClick={signOut}>Sign out</button>
        </div>
      </aside>
      <main className="min-w-0 flex-1 p-3 sm:p-4">{children}</main>
    </div>
  );
}
