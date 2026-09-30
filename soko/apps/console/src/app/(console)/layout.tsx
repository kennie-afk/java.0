import { redirect } from "next/navigation";
import { Topbar, type NavItem } from "@/components/topbar";
import { readSession } from "@/lib/session";

const ITEMS: NavItem[] = [
  { href: "/", label: "Overview" },
  { href: "/orders", label: "Orders" },
  { href: "/catalogue", label: "Catalogue" },
  { href: "/suppliers", label: "Suppliers" },
  { href: "/offers", label: "Offers" },
  { href: "/customers", label: "Customers" },
  { href: "/wastage", label: "Wastage" }
];

// Billing is the owner's business; an operator would only meet a 401 there.
const OWNER_ITEMS: NavItem[] = [{ href: "/billing", label: "Billing" }];

export default async function ConsoleLayout({ children }: { children: React.ReactNode }) {
  const session = await readSession();
  if (!session) {
    redirect("/welcome");
  }

  return (
    <div className="min-h-screen bg-[var(--color-canvas)]">
      <Topbar
        items={session.role === "OWNER" ? [...ITEMS, ...OWNER_ITEMS] : ITEMS}
        fullName={session.fullName}
        role={session.role}
        organisation={session.organisation ?? "Your organisation"}
      />
      <main className="mx-auto max-w-[96rem] px-5 py-8">{children}</main>
    </div>
  );
}
