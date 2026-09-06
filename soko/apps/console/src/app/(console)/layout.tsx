import { redirect } from "next/navigation";
import { Topbar, type NavItem } from "@/components/topbar";
import { readSession } from "@/lib/session";

const ITEMS: NavItem[] = [
  { href: "/", label: "Overview" },
  { href: "/orders", label: "Orders" },
  { href: "/catalogue", label: "Catalogue" },
  { href: "/suppliers", label: "Suppliers" },
  { href: "/offers", label: "Offers" }
];

export default async function ConsoleLayout({ children }: { children: React.ReactNode }) {
  const session = await readSession();
  if (!session) {
    redirect("/login");
  }

  return (
    <div className="min-h-screen bg-[var(--color-canvas)]">
      <Topbar
        items={ITEMS}
        fullName={session.fullName}
        role={session.role}
        organisation={session.organisation ?? "Your organisation"}
      />
      <main className="mx-auto max-w-6xl px-5 py-8">{children}</main>
    </div>
  );
}
