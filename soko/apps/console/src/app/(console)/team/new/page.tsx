import Link from "next/link";
import { redirect } from "next/navigation";
import { readSession } from "@/lib/session";
import { PageHeader, secondaryButtonClass } from "@/components/ui";
import { InviteForm } from "@/components/invite-form";

export default async function NewAccountPage() {
  const session = await readSession();
  if (session?.role !== "OWNER") redirect("/");
  return (
    <>
      <PageHeader title="Add account" subtitle="Only an owner can add accounts. Supplier and customer logins are tied to a supplier or customer record."
        actions={<Link href="/team" className={secondaryButtonClass}>Back</Link>} />
      <div className="max-w-lg"><InviteForm /></div>
    </>
  );
}
