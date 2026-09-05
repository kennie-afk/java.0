import { redirect } from "next/navigation";
import { SignOutButton } from "@/components/sign-out-button";
import { SidebarNav, type NavSection } from "@/components/sidebar-nav";
import { readToken } from "@/lib/session";

const SECTIONS: NavSection[] = [
  {
    key: "operations",
    label: "Operations",
    items: [
      { href: "/", label: "Overview" },
      { href: "/farms", label: "Farms" },
      { href: "/seasons", label: "Seasons" }
    ]
  },
  {
    key: "commerce",
    label: "People and trade",
    items: [
      { href: "/workforce", label: "Workforce" },
      { href: "/marketplace", label: "Marketplace" }
    ]
  },
  {
    key: "assurance",
    label: "Assurance",
    items: [{ href: "/fraud", label: "Fraud" }]
  }
];

export default async function DashboardLayout({ children }: { children: React.ReactNode }) {
  const token = await readToken();
  if (!token) {
    redirect("/login");
  }

  return (
    <div className="flex min-h-screen">
      <aside className="hidden w-56 shrink-0 flex-col border-r border-[var(--color-line)] bg-[var(--color-surface)] md:flex">
        <div className="px-5 py-6">
          <span className="text-base font-semibold tracking-tight">SmartSeason</span>
        </div>
        <SidebarNav sections={SECTIONS} />
        <div className="border-t border-[var(--color-line)] p-3">
          <SignOutButton />
        </div>
      </aside>
      <main className="flex-1 px-6 py-8 md:px-10">{children}</main>
    </div>
  );
}
