"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Logo } from "./logo";
import { useTerminalStatus } from "./use-status";
import { useStaffSession } from "./use-session";
import { useSyncState } from "./use-sync";

const LINKS = [
  { href: "/", label: "Status" },
  { href: "/sale", label: "Sale" },
  { href: "/catalogue", label: "Catalogue" },
  { href: "/journal", label: "Journal" },
  { href: "/summary", label: "Summary" },
  { href: "/signin", label: "Staff" },
  { href: "/enrol", label: "Enrolment" },
  { href: "/settings", label: "Settings" }
];

function active(path: string, href: string) {
  return href === "/" ? path === "/" : path === href || path.startsWith(href + "/");
}

export function Nav() {
  const path = usePathname();
  const s = useTerminalStatus();
  const { session } = useStaffSession();
  const sync = useSyncState();
  return (
    <aside className="no-print flex shrink-0 flex-col gap-3 border-b border-line bg-surface p-3 md:w-48 md:border-b-0 md:border-r">
      <Link href="/" aria-label="Mara terminal home">
        <Logo />
      </Link>
      <nav aria-label="Primary" className="flex flex-row flex-wrap gap-1 md:flex-col">
        {LINKS.map((l) => (
          <Link key={l.href} href={l.href} className="navlink" aria-current={active(path, l.href) ? "page" : undefined}>
            {l.label}
          </Link>
        ))}
      </nav>
      <div className="mt-auto space-y-1 border-t border-line pt-2 text-2xs text-muted">
        <div>{session ? `${session.displayName} (${session.role.toLowerCase()})` : "Nobody signed in"}</div>
        <div>{s.identity ? `Terminal ${s.identity.terminalId}` : "Not enrolled"}</div>
        <div>{s.online ? "Network: online" : "Network: offline"}</div>
        <div>{!sync || sync.syncedThrough === 0 ? "Sync: nothing uploaded yet" : `Sync: verified through #${sync.syncedThrough}`}</div>
      </div>
    </aside>
  );
}
