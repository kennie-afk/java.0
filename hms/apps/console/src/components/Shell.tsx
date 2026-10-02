"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useSession } from "@/lib/session";

type Item = { href: string; label: string; perm: string };
const NAV: { group: string; items: Item[] }[] = [
  { group: "Front desk", items: [
    { href: "/", label: "Overview", perm: "" },
    { href: "/patients", label: "Patients", perm: "patients:read" },
    { href: "/queue", label: "Queue", perm: "scheduling:read" },
    { href: "/appointments", label: "Appointments", perm: "scheduling:read" }] },
  { group: "Care", items: [
    { href: "/wards", label: "Wards and beds", perm: "inpatient:read" },
    { href: "/lab", label: "Laboratory", perm: "lab:read" },
    { href: "/pharmacy", label: "Pharmacy", perm: "pharmacy:read" }] },
  { group: "Money", items: [
    { href: "/billing", label: "Billing", perm: "billing:read" },
    { href: "/claims", label: "Claims (Madai)", perm: "claims:read" },
    { href: "/reports", label: "Reports", perm: "reports:read" }] },
  { group: "Admin", items: [
    { href: "/admin/staff", label: "Staff", perm: "staff:read" },
    { href: "/admin/roles", label: "Roles", perm: "staff:read" },
    { href: "/admin/facilities", label: "Facilities", perm: "facilities:manage" },
    { href: "/admin/audit", label: "Audit", perm: "audit:read" }] }
];

export function Shell({ children }: { children: React.ReactNode }) {
  const { me, can, facilityId, setFacility } = useSession();
  const path = usePathname();
  const signOut = async () => {
    await fetch("/api/session", { method: "DELETE" });
    window.location.href = "/login";
  };
  return (
    <div className="flex min-h-screen">
      <aside className="sticky top-0 hidden h-screen w-48 shrink-0 flex-col border-r border-line bg-surface sm:flex">
        <div className="border-b border-line px-3 py-2.5 text-lg font-semibold text-accent">HMS</div>
        <nav className="flex-1 space-y-3 overflow-y-auto px-2 py-3">
          {NAV.map((g) => {
            const items = g.items.filter((i) => !i.perm || can(i.perm));
            if (!items.length) return null;
            return (
              <div key={g.group}>
                <div className="px-2 pb-1 text-2xs font-semibold uppercase tracking-wide text-faint">{g.group}</div>
                {items.map((i) => {
                  const active = i.href === "/" ? path === "/" : path.startsWith(i.href);
                  return (
                    <Link key={i.href} href={i.href} className={`block rounded-md px-2 py-1 text-xs ${active ? "bg-accent-soft font-semibold text-accent" : "text-ink hover:bg-raised"}`}>
                      {i.label}
                    </Link>
                  );
                })}
              </div>
            );
          })}
        </nav>
        <div className="space-y-1 border-t border-line p-2 text-xs">
          <select value={facilityId} onChange={(e) => setFacility(e.target.value)} aria-label="Facility" className="w-full rounded-md border border-line bg-surface px-1.5 py-1 text-xs">
            {me.facilities.map((f) => <option key={f.id} value={f.id}>{f.name}</option>)}
          </select>
          <div className="truncate px-0.5 text-muted">{me.fullName}</div>
          <button onClick={signOut} className="px-0.5 text-accent hover:underline">Sign out</button>
        </div>
      </aside>
      <main className="min-w-0 flex-1">{children}</main>
    </div>
  );
}
