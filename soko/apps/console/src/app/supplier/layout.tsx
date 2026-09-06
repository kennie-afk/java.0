import { redirect } from "next/navigation";
import { RoleBar } from "@/components/role-bar";
import { readSession } from "@/lib/session";

export default async function SupplierLayout({ children }: { children: React.ReactNode }) {
  const session = await readSession();
  if (!session) {
    redirect("/login");
  }
  if (session.role !== "SUPPLIER") {
    redirect("/");
  }

  return (
    <div className="min-h-screen bg-[var(--color-canvas)]">
      <RoleBar
        badge="Supplier"
        items={[
          { href: "/supplier", label: "Orders to fill" },
          { href: "/supplier/offers", label: "What I sell" }
        ]}
        fullName={session.fullName}
        organisation={session.organisation ?? ""}
      />
      <main className="mx-auto max-w-5xl px-5 py-8">{children}</main>
    </div>
  );
}
