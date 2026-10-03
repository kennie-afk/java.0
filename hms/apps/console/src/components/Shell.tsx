"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState } from "react";
import { Icon, type IconName } from "@/components/icons";
import { useSession } from "@/lib/session";
import { SyncStatus } from "@/components/SyncStatus";
import { clearOfflineCaches, listQueued } from "@/lib/offline";

type Item = { href: string; label: string; perm: string; icon: IconName };
const NAV: { group: string; items: Item[] }[] = [
  { group: "Front desk", items: [
    { href: "/", label: "Overview", perm: "", icon: "home" },
    { href: "/patients", label: "Patients", perm: "patients:read", icon: "patients" },
    { href: "/queue", label: "Queue", perm: "scheduling:read", icon: "queue" },
    { href: "/appointments", label: "Appointments", perm: "scheduling:read", icon: "calendar" },
    { href: "/portal-requests", label: "Portal requests", perm: "portal:manage", icon: "inbox" }] },
  { group: "Care", items: [
    { href: "/wards", label: "Wards and beds", perm: "inpatient:read", icon: "bed" },
    { href: "/maternal", label: "Maternal and child", perm: "mch:read", icon: "pulse" },
    { href: "/lab", label: "Laboratory", perm: "lab:read", icon: "flask" },
    { href: "/imaging", label: "Imaging", perm: "imaging:read", icon: "inbox" },
    { href: "/programmes", label: "Programmes", perm: "programmes:read", icon: "clock" },
    { href: "/pharmacy", label: "Pharmacy", perm: "pharmacy:read", icon: "pill" }] },
  { group: "Money", items: [
    { href: "/billing", label: "Billing", perm: "billing:read", icon: "receipt" },
    { href: "/claims", label: "Claims (Madai)", perm: "claims:read", icon: "shield" },
    { href: "/reports", label: "Reports", perm: "reports:read", icon: "chart" }] },
  { group: "Admin", items: [
    { href: "/admin/staff", label: "Staff", perm: "staff:read", icon: "staff" },
    { href: "/admin/roles", label: "Roles", perm: "staff:read", icon: "key" },
    { href: "/admin/facilities", label: "Facilities", perm: "facilities:manage", icon: "building" },
    { href: "/admin/integrations", label: "Integrations", perm: "fhir:read", icon: "key" },
    { href: "/admin/audit", label: "Audit", perm: "audit:read", icon: "audit" }] }
];

function Brand() {
  return (
    <Link href="/" aria-label="HMS overview" className="flex items-center gap-2.5">
      {/* eslint-disable-next-line @next/next/no-img-element */}
      <img src="/logo-icon.flat.svg" alt="" className="h-8 w-8" />
      <span className="leading-tight">
        <span className="block font-[family-name:var(--font-display)] text-base font-semibold tracking-[-0.01em]">HMS</span>
        <span className="block text-xs text-muted">Health management</span>
      </span>
    </Link>
  );
}

function SidebarBody({ path }: { path: string }) {
  const { me, can, facilityId, setFacility } = useSession();
  const signOut = async () => {
    const waiting = (await listQueued()).length;
    // Entries that were never sent stay on this device; say so rather than silently keep or lose them.
    if (waiting > 0 && !window.confirm(`${waiting} entries have not been sent yet and will stay on this device until you sign in again and they send. Sign out anyway?`)) return;
    await clearOfflineCaches();
    await fetch("/api/session", { method: "DELETE" });
    window.location.href = "/login";
  };
  return (
    <>
      <div className="hidden border-b border-line px-4 py-2.5 lg:block"><Brand /></div>
      <nav className="flex-1 space-y-2.5 overflow-y-auto px-2.5 py-2.5" aria-label="Main">
        {NAV.map((g) => {
          const items = g.items.filter((i) => !i.perm || can(i.perm));
          if (!items.length) return null;
          return (
            <div key={g.group}>
              <div className="px-3 pb-1 text-xs font-semibold uppercase tracking-[0.07em] text-faint">{g.group}</div>
              <div className="space-y-0.5">
                {items.map((i) => {
                  const active = i.href === "/" ? path === "/" : path.startsWith(i.href);
                  return (
                    <Link key={i.href} href={i.href} aria-current={active ? "page" : undefined}
                      className={`relative flex items-center gap-2.5 rounded-lg px-3 py-[5px] max-lg:py-2 text-sm font-semibold ${active ? "bg-accent-soft text-accent-deep" : "text-muted hover:bg-raised hover:text-ink"}`}>
                      {active && <span className="absolute inset-y-1.5 left-0 w-[3px] rounded-r bg-accent" aria-hidden="true" />}
                      <Icon name={i.icon} className={`h-4 w-4 shrink-0 ${active ? "text-accent" : ""}`} />
                      {i.label}
                    </Link>
                  );
                })}
              </div>
            </div>
          );
        })}
      </nav>
      <SyncStatus />
      <div className="space-y-2 border-t border-line p-2.5">
        <label className="block">
          
          <select value={facilityId} onChange={(e) => setFacility(e.target.value)} aria-label="Facility"
            className="w-full cursor-pointer rounded-lg border border-line-strong bg-surface px-3 py-1.5 text-sm font-semibold">
            {me.facilities.map((f) => <option key={f.id} value={f.id}>{f.name}</option>)}
          </select>
        </label>
        <div className="flex items-center justify-between gap-2">
          <div className="min-w-0">
            <div className="truncate text-sm font-semibold">{me.fullName}</div>
            <div className="text-xs text-muted">Signed in</div>
          </div>
          <button onClick={signOut} aria-label="Sign out" title="Sign out" className="flex h-9 w-9 shrink-0 cursor-pointer items-center justify-center rounded-lg text-muted hover:bg-raised hover:text-ink">
            <Icon name="logout" className="h-5 w-5" />
          </button>
        </div>
      </div>
    </>
  );
}

export function Shell({ children }: { children: React.ReactNode }) {
  const path = usePathname();
  const [drawer, setDrawer] = useState(false);
  // A drawer left open across a navigation would cover the page it just opened.
  useEffect(() => setDrawer(false), [path]);
  return (
    <div className="flex min-h-screen">
      <aside className="sticky top-0 hidden h-screen w-[248px] shrink-0 flex-col border-r border-line bg-surface lg:flex">
        <SidebarBody path={path} />
      </aside>
      <div className="min-w-0 flex-1">
        <header className="sticky top-0 z-40 flex h-14 items-center justify-between border-b border-line bg-surface px-4 lg:hidden">
          <Brand />
          <button type="button" aria-label="Open menu" onClick={() => setDrawer(true)} className="flex h-10 w-10 cursor-pointer items-center justify-center rounded-lg hover:bg-raised">
            <Icon name="menu" className="h-5 w-5" />
          </button>
        </header>
        {drawer && (
          <div className="fixed inset-0 z-[70] lg:hidden" role="dialog" aria-modal="true" aria-label="Menu">
            <button type="button" aria-label="Close menu" className="absolute inset-0 cursor-default bg-[rgba(15,32,39,0.45)]" onClick={() => setDrawer(false)} />
            <aside className="absolute inset-y-0 left-0 flex w-[280px] max-w-[86vw] flex-col bg-surface shadow-[var(--shadow-lift)]">
              <div className="flex items-center justify-between border-b border-line px-5 py-3.5">
                <Brand />
                <button type="button" aria-label="Close menu" onClick={() => setDrawer(false)} className="flex h-9 w-9 cursor-pointer items-center justify-center rounded-lg text-muted hover:bg-raised">
                  <Icon name="close" className="h-5 w-5" />
                </button>
              </div>
              <SidebarBody path={path} />
            </aside>
          </div>
        )}
        <main>{children}</main>
      </div>
    </div>
  );
}
