import { redirect } from "next/navigation";
import { RoleBar } from "@/components/role-bar";
import { readSession } from "@/lib/session";

export default async function ShopLayout({ children }: { children: React.ReactNode }) {
  const session = await readSession();
  if (!session) {
    redirect("/login");
  }
  if (session.role !== "CUSTOMER") {
    redirect("/");
  }

  return (
    <div className="min-h-screen bg-[var(--color-canvas)]">
      <RoleBar
        badge="Buyer"
        items={[
          { href: "/shop", label: "Shop" },
          { href: "/shop/orders", label: "My orders" }
        ]}
        fullName={session.fullName}
        organisation={session.organisation ?? ""}
      />
      <main className="mx-auto max-w-5xl px-5 py-8">{children}</main>
    </div>
  );
}
