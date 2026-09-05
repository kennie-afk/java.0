import Link from "next/link";
import { redirect } from "next/navigation";
import { SignOutButton } from "@/components/sign-out-button";
import { readToken } from "@/lib/session";

const NAV = [
  { href: "/", label: "Overview" },
  { href: "/farms", label: "Farms" },
  { href: "/seasons", label: "Seasons" },
  { href: "/workforce", label: "Workforce" },
  { href: "/fraud", label: "Fraud" },
  { href: "/marketplace", label: "Marketplace" }
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
        <nav className="flex flex-1 flex-col gap-0.5 px-3">
          {NAV.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              className="rounded-md px-3 py-2 text-sm text-[var(--color-muted)] transition-colors hover:bg-[var(--color-brand-soft)] hover:text-[var(--color-brand)]"
            >
              {item.label}
            </Link>
          ))}
        </nav>
        <div className="border-t border-[var(--color-line)] p-3">
          <SignOutButton />
        </div>
      </aside>
      <main className="flex-1 px-6 py-8 md:px-10">{children}</main>
    </div>
  );
}
